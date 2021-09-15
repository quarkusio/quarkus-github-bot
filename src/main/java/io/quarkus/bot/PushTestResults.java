package io.quarkus.bot;

import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.test.JobResult;
import io.quarkus.bot.test.QuarkusStatusClient;
import io.quarkus.bot.test.TestResult;
import io.quarkus.bot.test.WorkflowResult;
import io.quarkus.bot.workflow.ArtifactsAreReady;
import io.quarkus.bot.workflow.GHWorkflowJobComparator;
import io.quarkus.bot.workflow.StackTraceUtils;
import io.quarkus.bot.workflow.WorkflowConstants;
import io.quarkus.bot.workflow.WorkflowReportFormatter;
import io.quarkus.bot.workflow.WorkflowRunAnalyzer;
import io.quarkus.bot.workflow.report.WorkflowReport;
import io.quarkus.bot.workflow.report.WorkflowReportJob;
import io.quarkus.bot.workflow.report.WorkflowReportModule;
import io.quarkus.bot.workflow.report.WorkflowReportTestCase;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRun.AnnotationLevel;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHCheckRunBuilder.Annotation;
import org.kohsuke.github.GHCheckRunBuilder.Output;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class PushTestResults {

    private static final Logger LOG = Logger.getLogger(PushTestResults.class);

    private static final int GITHUB_FIELD_LENGTH_HARD_LIMIT = 65000;

    @Inject
    WorkflowRunAnalyzer workflowRunAnalyzer;

    @Inject
    WorkflowReportFormatter workflowReportFormatter;

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    @RestClient
    QuarkusStatusClient quarkusStatusClient;

    void analyzeWorkflowResults(@WorkflowRun.Completed GHEventPayload.WorkflowRun workflowRunPayload)
            throws IOException {
        GHWorkflowRun workflowRun = workflowRunPayload.getWorkflowRun();
        GHWorkflow workflow = workflowRunPayload.getWorkflow();

        if (!WorkflowConstants.QUARKUS_CI_WORKFLOW_NAME.equals(workflow.getName())) {
            return;
        }
        if (workflowRun.getEvent() != GHEvent.PULL_REQUEST) {
            return;
        }

        List<GHArtifact> artifacts;
        try {
            ArtifactsAreReady artifactsAreReady = new ArtifactsAreReady(workflowRun);
            Awaitility.await()
                    .atMost(Duration.ofMinutes(5))
                    .pollDelay(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofSeconds(30))
                    .ignoreExceptions()
                    .until(artifactsAreReady);
            artifacts = artifactsAreReady.getArtifacts();
        } catch (ConditionTimeoutException e) {
            LOG.warn("Workflow run #" + workflowRun.getId()
                    + " - Unable to get the artifacts in a timely manner, ignoring them");
            return;
        }

        Optional<GHPullRequest> pullRequestOptional = getAssociatedPullRequest(workflowRun, artifacts);
        if (pullRequestOptional.isEmpty()) {
            LOG.error("Workflow run #" + workflowRun.getId() + " - Unable to find the associated pull request");
            return;
        }
        GHPullRequest pullRequest = pullRequestOptional.get();

        HideOutdatedWorkflowRunResults.hideOutdatedWorkflowRunResults(quarkusBotConfig, pullRequest);

        if (pullRequest.isDraft()) {
            return;
        }

        List<GHArtifact> surefireReportsArtifacts = artifacts
                .stream()
                .filter(a -> a.getName().startsWith(WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX))
                .sorted((a1, a2) -> a1.getName().compareTo(a2.getName()))
                .collect(Collectors.toList());

        List<GHWorkflowJob> jobs = workflowRun.listJobs().toList()
                .stream()
                .sorted(GHWorkflowJobComparator.INSTANCE)
                .collect(Collectors.toList());

        Optional<WorkflowReport> workflowReportOptional = workflowRunAnalyzer.getTestReport(workflowRun, pullRequest, jobs,
                surefireReportsArtifacts);
        if (workflowReportOptional.isEmpty()) {
            return; // the reason was logged by workflowRunAnalyzer
        }
        WorkflowReport workflowReport = workflowReportOptional.get();

        List<JobResult> jobResults = new ArrayList<>();
        for (WorkflowReportJob job : workflowReport.getJobs()) {
            List<TestResult> testResults = new ArrayList<>();
            for (WorkflowReportModule module : job.getModules()) {
                for (ReportTestSuite reportTestSuite : module.getReportTestSuites()) {
                    for (ReportTestCase testCase : reportTestSuite.getTestCases()) {
                        if (!testCase.hasSkipped()) {
                            String testFullName = testCase.getFullName();
                            TestResult result = new TestResult(testFullName, testCase.isSuccessful());
                            testResults.add(result);
                        }
                    }
                }
            }
            jobResults.add(new JobResult(job.getUrl(), job.getName(), testResults, job.getCompletedAt()));
        }

        quarkusStatusClient.storeTestResults(new WorkflowResult(jobResults, workflowReport.getSha()));
    }

    /**
     * Unfortunately when the pull request is coming from a fork, the pull request is not in the payload
     * so we use a dirty trick to get it.
     * We use the sha as last resort as the workflow takes some time and the sha might not be associated to the pull request
     * anymore.
     */
    private Optional<GHPullRequest> getAssociatedPullRequest(GHWorkflowRun workflowRun, List<GHArtifact> artifacts)
            throws NumberFormatException, IOException {
        Optional<GHArtifact> pullRequestNumberArtifact = artifacts.stream()
                .filter(a -> a.getName().startsWith(WorkflowConstants.PULL_REQUEST_NUMBER_PREFIX)).findFirst();
        if (!pullRequestNumberArtifact.isEmpty()) {
            GHPullRequest pullRequest = workflowRun.getRepository().getPullRequest(
                    Integer.valueOf(
                            pullRequestNumberArtifact.get().getName().replace(WorkflowConstants.PULL_REQUEST_NUMBER_PREFIX,
                                    "")));
            return Optional.of(pullRequest);
        }

        LOG.warn("Workflow run #" + workflowRun.getId() + " - Unable to get the pull request artifact, trying with sha");

        List<GHPullRequest> pullRequests = workflowRun.getRepository().queryPullRequests()
                .state(GHIssueState.OPEN)
                .head(workflowRun.getHeadRepository().getOwnerName() + ":" + workflowRun.getHeadBranch())
                .list().toList();
        if (!pullRequests.isEmpty()) {
            return Optional.of(pullRequests.get(0));
        }

        return Optional.empty();
    }

    private Optional<GHCheckRun> createCheckRun(GHWorkflowRun workflowRun, GHPullRequest pullRequest,
            boolean artifactsAvailable, WorkflowReport workflowReport) {
        if (!workflowReport.hasTestFailures() || quarkusBotConfig.isDryRun()) {
            return Optional.empty();
        }

        try {
            String name = "Build summary for " + workflowRun.getHeadSha();
            String summary = workflowReportFormatter.getCheckRunReportSummary(workflowReport, pullRequest, artifactsAvailable);
            String checkRunReport = workflowReportFormatter.getCheckRunReport(workflowReport, true);
            if (checkRunReport.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
                checkRunReport = workflowReportFormatter.getCheckRunReport(workflowReport, false);
            }

            Output checkRunOutput = new Output(name, summary).withText(checkRunReport);

            for (WorkflowReportJob workflowReportJob : workflowReport.getJobs()) {
                if (!workflowReportJob.hasTestFailures()) {
                    continue;
                }

                List<WorkflowReportTestCase> annotatedWorkflowReportTestCases = workflowReportJob.getModules().stream()
                        .filter(m -> m.hasTestFailures())
                        .flatMap(m -> m.getTestFailures().stream())
                        .collect(Collectors.toList());

                for (WorkflowReportTestCase workflowReportTestCase : annotatedWorkflowReportTestCases) {
                    checkRunOutput.add(new Annotation(workflowReportTestCase.getClassPath(),
                            StringUtils.isNumeric(workflowReportTestCase.getFailureErrorLine())
                                    ? Integer.valueOf(workflowReportTestCase.getFailureErrorLine())
                                    : 1,
                            AnnotationLevel.FAILURE,
                            StringUtils.isNotBlank(workflowReportTestCase.getFailureDetail()) ? StackTraceUtils
                                    .firstLines(StackTraceUtils.abbreviate(workflowReportTestCase.getFailureDetail(),
                                            GITHUB_FIELD_LENGTH_HARD_LIMIT), 3)
                                    : "The test failed.")
                                            .withTitle(StringUtils.abbreviate(workflowReportJob.getName(), 255))
                                            .withRawDetails(
                                                    StackTraceUtils.abbreviate(workflowReportTestCase.getFailureDetail(),
                                                            GITHUB_FIELD_LENGTH_HARD_LIMIT)));
                }
            }

            GHCheckRunBuilder checkRunBuilder = workflowRun.getRepository().createCheckRun(name, workflowRun.getHeadSha())
                    .add(checkRunOutput)
                    .withConclusion(GHCheckRun.Conclusion.NEUTRAL)
                    .withCompletedAt(new Date());

            return Optional.of(checkRunBuilder.create());
        } catch (Exception e) {
            LOG.error("Pull request #" + pullRequest.getNumber() + " - Unable to create check run for test failures", e);
            return Optional.empty();
        }
    }
}
