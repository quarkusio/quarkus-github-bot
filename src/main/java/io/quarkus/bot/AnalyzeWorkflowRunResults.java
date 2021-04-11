package io.quarkus.bot;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
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
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.workflow.WorkflowConstants;
import io.quarkus.bot.workflow.WorkflowReportFormatter;
import io.quarkus.bot.workflow.WorkflowRunAnalyzer;
import io.quarkus.bot.workflow.report.WorkflowReport;
import io.quarkus.bot.workflow.report.WorkflowReportTestCase;

@SuppressWarnings("deprecation")
public class AnalyzeWorkflowRunResults {

    private static final Logger LOG = Logger.getLogger(AnalyzeWorkflowRunResults.class);

    @Inject
    WorkflowRunAnalyzer workflowRunAnalyzer;

    @Inject
    WorkflowReportFormatter workflowReportFormatter;

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void analyzeWorkflowResults(@WorkflowRun.Completed GHEventPayload.WorkflowRun workflowRunPayload)
            throws IOException {
        GHWorkflowRun workflowRun = workflowRunPayload.getWorkflowRun();
        GHWorkflow workflow = workflowRunPayload.getWorkflow();

        if (!WorkflowConstants.QUARKUS_CI_WORKFLOW_NAME.equals(workflow.getName())) {
            return;
        }
        if (workflowRun.getConclusion() != Conclusion.FAILURE && workflowRun.getConclusion() != Conclusion.CANCELLED) {
            return;
        }
        if (workflowRun.getEvent() != GHEvent.PULL_REQUEST) {
            return;
        }

        List<GHArtifact> artifacts = workflowRun.listArtifacts().toList();

        Optional<GHPullRequest> pullRequestOptional = getAssociatedPullRequest(workflowRun, artifacts);
        if (pullRequestOptional.isEmpty()) {
            LOG.error("Workflow run #" + workflowRun.getId() + " - Unable to find the associated pull request");
            return;
        }
        GHPullRequest pullRequest = pullRequestOptional.get();

        List<GHArtifact> surefireReportsArtifacts = artifacts
                .stream()
                .filter(a -> a.getName().startsWith(WorkflowConstants.SUREFIRE_REPORTS_ARTIFACT_PREFIX))
                .sorted((a1, a2) -> a1.getName().compareTo(a2.getName()))
                .collect(Collectors.toList());

        List<GHWorkflowJob> jobs = workflowRun.listJobs().toList()
                .stream()
                .sorted((j1, j2) -> j1.getName().compareTo(j2.getName()))
                .collect(Collectors.toList());

        Optional<WorkflowReport> workflowReportOptional = workflowRunAnalyzer.getReport(workflowRun, pullRequest, jobs,
                surefireReportsArtifacts);
        if (workflowReportOptional.isEmpty()) {
            return;
        }

        WorkflowReport workflowReport = workflowReportOptional.get();

        Optional<GHCheckRun> checkRunOptional = createCheckRun(workflowRun, pullRequest, workflowReport);

        String commentReport = workflowReportFormatter.getCommentReport(workflowReport,
                checkRunOptional.orElse(null),
                WorkflowConstants.MESSAGE_ID_ACTIVE);
        if (!quarkusBotConfig.isDryRun()) {
            pullRequest.comment(commentReport);
        } else {
            LOG.info("Workflow run #" + workflowRun.getId() + " - Add test failures:\n" + commentReport);
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
            WorkflowReport workflowReport) {
        if (!workflowReport.hasTestFailures() || quarkusBotConfig.isDryRun()) {
            return Optional.empty();
        }

        String title = "Build summary for " + workflowRun.getHeadSha();
        String summary = workflowReportFormatter.getCheckRunReportSummary(workflowReport, pullRequest);

        try {
            Output checkRunOutput = new Output(title, summary)
                    .withText(workflowReportFormatter.getCheckRunReport(workflowReport));

            List<WorkflowReportTestCase> annotatedWorkflowReportTestCases = workflowReport.getJobs().stream()
                    .filter(j -> j.hasTestFailures())
                    .flatMap(j -> j.getModules().stream())
                    .filter(m -> m.hasTestFailures())
                    .flatMap(m -> m.getFailures().stream())
                    .filter(f -> StringUtils.isNumeric(f.getFailureErrorLine()))
                    .collect(Collectors.toList());
            for (WorkflowReportTestCase workflowReportTestCase : annotatedWorkflowReportTestCases) {
                checkRunOutput.add(new Annotation(workflowReportTestCase.getClassPath(),
                        Integer.valueOf(workflowReportTestCase.getFailureErrorLine()),
                        AnnotationLevel.FAILURE,
                        workflowReportTestCase.getFailureDetail() != null
                                ? StringUtils.abbreviate(workflowReportTestCase.getFailureDetail(), 65000)
                                : null)
                                        .withTitle(StringUtils.abbreviate(workflowReportTestCase.getFailureType(), 255)));
            }

            GHCheckRunBuilder checkRun = workflowRun.getRepository().createCheckRun(title, workflowRun.getHeadSha())
                    .add(checkRunOutput)
                    .withConclusion(GHCheckRun.Conclusion.from(workflowRun.getConclusion().name()))
                    .withCompletedAt(new Date());

            return Optional.of(checkRun.create());
        } catch (Exception e) {
            LOG.error("Pull request #" + pullRequest.getNumber() + " - Unable to create check run for test failures\ntitle="
                    + title + "\nsummary=" + summary, e);
            return Optional.empty();
        }
    }
}
