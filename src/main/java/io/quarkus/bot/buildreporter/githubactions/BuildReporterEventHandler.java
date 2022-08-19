package io.quarkus.bot.buildreporter.githubactions;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

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
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Conclusion;
import org.kohsuke.github.GitHub;

import io.quarkiverse.githubapp.event.Actions;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReport;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportJob;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportJobIncludeStrategy;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportTestCase;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

@Singleton
public class BuildReporterEventHandler {

    private static final Logger LOG = Logger.getLogger(BuildReporterEventHandler.class);

    private static final int GITHUB_FIELD_LENGTH_HARD_LIMIT = 65000;

    @Inject
    WorkflowRunAnalyzer workflowRunAnalyzer;

    @Inject
    WorkflowReportFormatter workflowReportFormatter;

    @Inject
    WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy;

    @Inject
    StackTraceShortener stackTraceShortener;

    public void handle(GHEventPayload.WorkflowRun workflowRunPayload,
            BuildReporterConfig buildReporterConfig,
            GitHub gitHub, DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (buildReporterConfig.getMonitoredWorkflows() == null ||
                buildReporterConfig.getMonitoredWorkflows().isEmpty()) {
            return;
        }

        GHWorkflowRun workflowRun = workflowRunPayload.getWorkflowRun();
        GHWorkflow workflow = workflowRunPayload.getWorkflow();

        if (!buildReporterConfig.getMonitoredWorkflows().contains(workflow.getName())) {
            return;
        }

        switch (workflowRunPayload.getAction()) {
            case Actions.COMPLETED:
                handleCompleted(workflowRun, buildReporterConfig, gitHub, gitHubGraphQLClient);
                break;
            case Actions.REQUESTED:
                handleRequested(workflowRun, buildReporterConfig, gitHub, gitHubGraphQLClient);
                break;
            default:
                // we don't do anything for other actions
                break;
        }
    }

    private void handleCompleted(GHWorkflowRun workflowRun,
            BuildReporterConfig buildReporterConfig,
            GitHub gitHub,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
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
            if (workflowRun.getConclusion() != Conclusion.CANCELLED) {
                LOG.warn("Workflow run #" + workflowRun.getId()
                        + " - Unable to get the artifacts in a timely manner, ignoring them");
            }
            artifacts = Collections.emptyList();
            artifactsAvailable = false;
        }

        if (workflowRun.getEvent() == GHEvent.PULL_REQUEST) {
            Optional<GHPullRequest> pullRequestOptional = getAssociatedPullRequest(workflowRun, artifacts);

            if (pullRequestOptional.isEmpty()) {
                LOG.error("Workflow run #" + workflowRun.getId() + " - Unable to find the associated pull request");
                return;
            }

            GHPullRequest pullRequest = pullRequestOptional.get();
            WorkflowContext workflowContext = new WorkflowContext(pullRequest);

            hideOutdatedWorkflowRunResults(buildReporterConfig, workflowContext, pullRequest,
                    gitHubGraphQLClient);

            if (workflowRun.getConclusion() != Conclusion.FAILURE) {
                return;
            }

            Optional<String> reportCommentOptional = generateReportComment(workflowRun, buildReporterConfig, workflowContext,
                    artifacts, artifactsAvailable);

            if (reportCommentOptional.isEmpty()) {
                return;
            }

            String reportComment = reportCommentOptional.get();

            if (!buildReporterConfig.isDryRun()) {
                pullRequest.comment(reportComment);
            } else {
                LOG.info("Pull request #" + pullRequest.getNumber() + " - Add test failures:\n" + reportComment);
            }
        } else {
            Optional<GHIssue> reportIssueOptional = getAssociatedReportIssue(gitHub, workflowRun, artifacts);

            if (reportIssueOptional.isEmpty()) {
                return;
            }

            GHIssue reportIssue = reportIssueOptional.get();
            WorkflowContext workflowContext = new WorkflowContext(reportIssue);

            hideOutdatedWorkflowRunResults(buildReporterConfig, workflowContext, reportIssue,
                    gitHubGraphQLClient);

            if (workflowRun.getConclusion() == Conclusion.SUCCESS
                    && reportIssue.getState() == GHIssueState.OPEN) {
                String fixedComment = ":heavy_check_mark: **Build fixed:**\n* Link to latest CI run: "
                        + workflowRun.getHtmlUrl().toString();

                if (!buildReporterConfig.isDryRun()) {
                    reportIssue.comment(fixedComment);
                    reportIssue.close();
                } else {
                    LOG.info("Issue #" + reportIssue.getNumber() + " - Add comment: " + fixedComment);
                    LOG.info("Issue #" + reportIssue.getNumber() + " - Closing report issue");
                }
                return;
            }

            if (workflowRun.getConclusion() != Conclusion.FAILURE) {
                return;
            }

            if (!buildReporterConfig.isDryRun()) {
                reportIssue.reopen();
            } else {
                LOG.info("Issue #" + reportIssue.getNumber() + " - Reopening report issue");
            }

            Optional<String> reportCommentOptional = generateReportComment(workflowRun, buildReporterConfig, workflowContext,
                    artifacts, artifactsAvailable);

            if (reportCommentOptional.isEmpty()) {
                // not able to generate a proper report but let's post a default comment anyway
                String defaultFailureComment = "The build is failing and we were not able to generate a report:\n* Link to latest CI run: "
                        + workflowRun.getHtmlUrl().toString();

                if (!buildReporterConfig.isDryRun()) {
                    reportIssue.comment(defaultFailureComment);
                } else {
                    LOG.info("Issue #" + reportIssue.getNumber() + " - Add comment: " + defaultFailureComment);
                }
                return;
            }

            String reportComment = reportCommentOptional.get();

            if (!buildReporterConfig.isDryRun()) {
                reportIssue.comment(reportComment);
            } else {
                LOG.info("Issue #" + reportIssue.getNumber() + " - Add test failures:\n" + reportComment);
            }
        }
    }

    private Optional<String> generateReportComment(GHWorkflowRun workflowRun,
            BuildReporterConfig buildReporterConfig,
            WorkflowContext workflowContext,
            List<GHArtifact> artifacts,
            boolean artifactsAvailable) throws IOException {
        List<GHArtifact> surefireReportsArtifacts = artifacts
                .stream()
                .filter(a -> a.getName().startsWith(WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX))
                .sorted((a1, a2) -> a1.getName().compareTo(a2.getName()))
                .collect(Collectors.toList());

        List<GHWorkflowJob> jobs = workflowRun.listJobs().toList()
                .stream()
                .sorted(buildReporterConfig.getJobNameComparator())
                .collect(Collectors.toList());

        Optional<WorkflowReport> workflowReportOptional = workflowRunAnalyzer.getReport(workflowRun, workflowContext, jobs,
                surefireReportsArtifacts);
        if (workflowReportOptional.isEmpty()) {
            return Optional.empty();
        }

        WorkflowReport workflowReport = workflowReportOptional.get();

        Optional<GHCheckRun> checkRunOptional = createCheckRun(workflowRun, buildReporterConfig, workflowContext,
                artifactsAvailable, workflowReport);

        String reportComment = workflowReportFormatter.getReportComment(workflowReport,
                artifactsAvailable,
                checkRunOptional.orElse(null),
                WorkflowConstants.MESSAGE_ID_ACTIVE,
                true,
                true,
                workflowReportJobIncludeStrategy);
        if (reportComment.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
            reportComment = workflowReportFormatter.getReportComment(workflowReport,
                    artifactsAvailable,
                    checkRunOptional.orElse(null),
                    WorkflowConstants.MESSAGE_ID_ACTIVE,
                    false,
                    true,
                    workflowReportJobIncludeStrategy);
        }
        if (reportComment.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
            reportComment = workflowReportFormatter.getReportComment(workflowReport,
                    artifactsAvailable,
                    checkRunOptional.orElse(null),
                    WorkflowConstants.MESSAGE_ID_ACTIVE,
                    false,
                    false,
                    workflowReportJobIncludeStrategy);
        }
        return Optional.of(reportComment);
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

    /**
     * It is possible to associate a build with an issue to report to.
     */
    private Optional<GHIssue> getAssociatedReportIssue(GitHub gitHub, GHWorkflowRun workflowRun, List<GHArtifact> artifacts)
            throws NumberFormatException, IOException {
        Optional<GHArtifact> reportIssueNumberArtifact = artifacts.stream()
                .filter(a -> a.getName().startsWith(WorkflowConstants.REPORT_ISSUE_NUMBER_PREFIX)).findFirst();
        if (!reportIssueNumberArtifact.isEmpty()) {
            String issueReference = reportIssueNumberArtifact.get().getName()
                    .replace(WorkflowConstants.REPORT_ISSUE_NUMBER_PREFIX, "");
            if (issueReference.contains("#")) {
                String[] issueReferenceParts = issueReference.split("#", 2);
                return Optional
                        .of(gitHub.getRepository(issueReferenceParts[0]).getIssue(Integer.valueOf(issueReferenceParts[1])));
            }

            return Optional.of(workflowRun.getRepository().getIssue(Integer.valueOf(issueReference)));
        }

        return Optional.empty();
    }

    private Optional<GHCheckRun> createCheckRun(GHWorkflowRun workflowRun,
            BuildReporterConfig buildReporterConfig,
            WorkflowContext workflowContext,
            boolean artifactsAvailable, WorkflowReport workflowReport) {
        if (!workflowReport.hasTestFailures() || buildReporterConfig.isDryRun()) {
            return Optional.empty();
        }

        try {
            String name = "Build summary for " + workflowRun.getHeadSha();
            String summary = workflowReportFormatter.getCheckRunReportSummary(workflowReport, workflowContext,
                    artifactsAvailable, workflowReportJobIncludeStrategy);
            String checkRunReport = workflowReportFormatter.getCheckRunReport(workflowReport, true, true);
            if (checkRunReport.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
                checkRunReport = workflowReportFormatter.getCheckRunReport(workflowReport, false, true);
            }
            if (checkRunReport.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
                checkRunReport = workflowReportFormatter.getCheckRunReport(workflowReport, false, false);
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
                            StringUtils.isNotBlank(workflowReportTestCase.getFailureDetail())
                                    ? stackTraceShortener.shorten(workflowReportTestCase.getFailureDetail(),
                                            GITHUB_FIELD_LENGTH_HARD_LIMIT, 3)
                                    : "The test failed.")
                            .withTitle(StringUtils.abbreviate(workflowReportJob.getName(), 255))
                            .withRawDetails(
                                    stackTraceShortener.shorten(workflowReportTestCase.getFailureDetail(),
                                            GITHUB_FIELD_LENGTH_HARD_LIMIT)));
                }
            }

            GHCheckRunBuilder checkRunBuilder = workflowRun.getRepository().createCheckRun(name, workflowRun.getHeadSha())
                    .add(checkRunOutput)
                    .withConclusion(GHCheckRun.Conclusion.NEUTRAL)
                    .withCompletedAt(new Date());

            return Optional.of(checkRunBuilder.create());
        } catch (Exception e) {
            LOG.error(workflowContext.getLogContext() + " - Unable to create check run for test failures", e);
            return Optional.empty();
        }
    }

    private void handleRequested(GHWorkflowRun workflowRun,
            BuildReporterConfig buildReporterConfig,
            GitHub gitHub, DynamicGraphQLClient gitHubGraphQLClient) throws IOException {

        List<GHPullRequest> pullRequests = workflowRun.getRepository().queryPullRequests()
                .state(GHIssueState.OPEN)
                .head(workflowRun.getHeadRepository().getOwnerName() + ":" + workflowRun.getHeadBranch())
                .list().toList();
        if (pullRequests.isEmpty()) {
            return;
        }

        hideOutdatedWorkflowRunResults(buildReporterConfig, new WorkflowContext(pullRequests.get(0)), pullRequests.get(0),
                gitHubGraphQLClient);
    }

    private static void hideOutdatedWorkflowRunResults(BuildReporterConfig buildReporterConfig,
            WorkflowContext workflowContext, GHIssue issue,
            DynamicGraphQLClient gitHubGraphQLClient)
            throws IOException {
        List<GHIssueComment> comments = issue.getComments();

        for (GHIssueComment comment : comments) {
            if (!comment.getBody().contains(WorkflowConstants.MESSAGE_ID_ACTIVE)) {
                continue;
            }

            StringBuilder updatedComment = new StringBuilder();
            updatedComment.append(WorkflowConstants.HIDE_MESSAGE_PREFIX);
            updatedComment.append(comment.getBody().replace(WorkflowConstants.MESSAGE_ID_ACTIVE,
                    WorkflowConstants.MESSAGE_ID_HIDDEN));

            if (!buildReporterConfig.isDryRun()) {
                try {
                    comment.update(updatedComment.toString());
                } catch (IOException e) {
                    LOG.error(workflowContext.getLogContext() +
                            " - Unable to hide outdated workflow run status for comment " + comment.getId());
                }
                try {
                    minimizeOutdatedComment(gitHubGraphQLClient, comment);
                } catch (ExecutionException | InterruptedException e) {
                    LOG.error(workflowContext.getLogContext() +
                            " - Unable to minimize outdated workflow run status for comment " + comment.getId());
                }
            } else {
                LOG.info(workflowContext.getLogContext() + " - Hide outdated workflow run status " + comment.getId());
            }
        }
    }

    private static void minimizeOutdatedComment(DynamicGraphQLClient gitHubGraphQLClient, GHIssueComment comment)
            throws ExecutionException, InterruptedException {
        Map<String, Object> variables = new HashMap<>();
        variables.put("subjectId", comment.getNodeId());
        gitHubGraphQLClient.executeSync("""
                mutation MinimizeOutdatedContent($subjectId: ID!) {
                  minimizeComment(input: {
                    subjectId: $subjectId,
                    classifier: OUTDATED}) {
                      minimizedComment {
                        isMinimized
                      }
                    }
                }
                """, variables);
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
