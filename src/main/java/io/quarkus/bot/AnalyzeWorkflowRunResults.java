package io.quarkus.bot;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
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
import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.workflow.StackTraceUtils;
import io.quarkus.bot.workflow.WorkflowConstants;
import io.quarkus.bot.workflow.WorkflowReportFormatter;
import io.quarkus.bot.workflow.WorkflowRunAnalyzer;
import io.quarkus.bot.workflow.report.WorkflowReport;
import io.quarkus.bot.workflow.report.WorkflowReportJob;
import io.quarkus.bot.workflow.report.WorkflowReportTestCase;

public class AnalyzeWorkflowRunResults {

    private static final Logger LOG = Logger.getLogger(AnalyzeWorkflowRunResults.class);

    private static final int GITHUB_FIELD_LENGTH_HARD_LIMIT = 65000;

    @Inject
    WorkflowRunAnalyzer workflowRunAnalyzer;

    @Inject
    WorkflowReportFormatter workflowReportFormatter;

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

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
        boolean artifactsAvailable;
        try {
            ArtifactsAreReady artifactsAreReady = new ArtifactsAreReady(workflowRun);
            Awaitility.await()
                    .atMost(Duration.ofMinutes(5))
                    .pollDelay(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofSeconds(30))
                    .ignoreExceptions()
                    .until(artifactsAreReady);
            artifacts = artifactsAreReady.getArtifacts();
            artifactsAvailable = true;
        } catch (ConditionTimeoutException e) {
            LOG.warn("Workflow run #" + workflowRun.getId()
                    + " - Unable to get the artifacts in a timely manner, ignoring them");
            artifacts = Collections.emptyList();
            artifactsAvailable = false;
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

        if (workflowRun.getConclusion() != Conclusion.FAILURE) {
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

        Optional<WorkflowReport> workflowReportOptional = workflowRunAnalyzer.getReport(workflowRun, pullRequest, jobs,
                surefireReportsArtifacts);
        if (workflowReportOptional.isEmpty()) {
            return;
        }

        WorkflowReport workflowReport = workflowReportOptional.get();

        Optional<GHCheckRun> checkRunOptional = createCheckRun(workflowRun, pullRequest, artifactsAvailable, workflowReport);

        String commentReport = workflowReportFormatter.getCommentReport(workflowReport,
                artifactsAvailable,
                checkRunOptional.orElse(null),
                WorkflowConstants.MESSAGE_ID_ACTIVE,
                true);
        if (commentReport.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
            commentReport = workflowReportFormatter.getCommentReport(workflowReport,
                    artifactsAvailable,
                    checkRunOptional.orElse(null),
                    WorkflowConstants.MESSAGE_ID_ACTIVE,
                    false);
        }
        if (!quarkusBotConfig.isDryRun()) {
            pullRequest.comment(commentReport);
        } else {
            LOG.info("Pull request #" + pullRequest.getNumber() + " - Add test failures:\n" + commentReport);
        }
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

    private final static class GHWorkflowJobComparator implements Comparator<GHWorkflowJob> {

        private static final GHWorkflowJobComparator INSTANCE = new GHWorkflowJobComparator();

        private static final String INITIAL_JDK_PREFIX = "Initial JDK ";

        @Override
        public int compare(GHWorkflowJob o1, GHWorkflowJob o2) {
            if (o1.getName().startsWith(INITIAL_JDK_PREFIX) && !o2.getName().startsWith(INITIAL_JDK_PREFIX)) {
                return -1;
            }
            if (!o1.getName().startsWith(INITIAL_JDK_PREFIX) && o2.getName().startsWith(INITIAL_JDK_PREFIX)) {
                return 1;
            }

            return o1.getName().compareTo(o2.getName());
        }

    }

    private final static class ArtifactsAreReady implements Callable<Boolean> {
        private final GHWorkflowRun workflowRun;
        private List<GHArtifact> artifacts;

        private ArtifactsAreReady(GHWorkflowRun workflowRun) {
            this.workflowRun = workflowRun;
        }

        @Override
        public Boolean call() throws Exception {
            artifacts = workflowRun.listArtifacts().toList();
            return !artifacts.isEmpty();
        }

        public List<GHArtifact> getArtifacts() {
            return artifacts;
        }
    }
}
