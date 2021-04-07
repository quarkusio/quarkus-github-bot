package io.quarkus.bot;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.workflow.JobReportsAnalyzer;
import io.quarkus.bot.workflow.SurefireReportsAnalyzer;

public class AnalyzeWorkflowRunResults {

    private static final Logger LOG = Logger.getLogger(AnalyzeWorkflowRunResults.class);

    public static final String SUREFIRE_REPORTS_ARTIFACT_PREFIX = "surefire-reports-";
    public static final String MESSAGE_ID_ACTIVE = "<!-- Quarkus-GitHub-Bot/msg-id:workflow-run-status-active -->";
    public static final String MESSAGE_ID_HIDDEN = "<!-- Quarkus-GitHub-Bot/msg-id:workflow-run-status-hidden -->";
    public static final String QUARKUS_CI_WORKFLOW_NAME = "Quarkus CI";
    private static final String PULL_REQUEST_NUMBER_PREFIX = "pull-request-number-";

    @Inject
    SurefireReportsAnalyzer surefireReportsAnalyzer;

    @Inject
    JobReportsAnalyzer jobReportsAnalyzer;

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void analyzeWorkflowResults(@WorkflowRun.Completed GHEventPayload.WorkflowRun workflowRunPayload)
            throws IOException {
        GHWorkflowRun workflowRun = workflowRunPayload.getWorkflowRun();
        GHWorkflow workflow = workflowRunPayload.getWorkflow();

        if (!QUARKUS_CI_WORKFLOW_NAME.equals(workflow.getName())) {
            return;
        }
        if (workflowRun.getConclusion() != Conclusion.FAILURE && workflowRun.getConclusion() != Conclusion.CANCELLED) {
            return;
        }
        if (workflowRun.getEvent() != GHEvent.PULL_REQUEST) {
            return;
        }

        // unfortunately when the pull request is coming from a fork, the pull request is not in the payload
        // so we use a dirty trick to get it
        List<GHArtifact> artifacts = workflowRun.listArtifacts().toList();

        Optional<GHArtifact> pullRequestNumberArtifact = artifacts.stream()
                .filter(a -> a.getName().startsWith(PULL_REQUEST_NUMBER_PREFIX)).findFirst();
        if (pullRequestNumberArtifact.isEmpty()) {
            return;
        }
        GHPullRequest pullRequest = workflowRunPayload.getRepository().getPullRequest(
                Integer.valueOf(pullRequestNumberArtifact.get().getName().replace(PULL_REQUEST_NUMBER_PREFIX, "")));
        List<GHArtifact> surefireReportsArtifacts = artifacts
                .stream()
                .filter(a -> a.getName().startsWith(SUREFIRE_REPORTS_ARTIFACT_PREFIX))
                .sorted((a1, a2) -> a1.getName().compareTo(a2.getName()))
                .collect(Collectors.toList());

        Set<String> surefireReportsArtifactNames = surefireReportsArtifacts.stream()
                .map(a -> a.getName().replace(SUREFIRE_REPORTS_ARTIFACT_PREFIX, ""))
                .collect(Collectors.toSet());

        List<GHWorkflowJob> jobs = workflowRun.listJobs().toList()
                .stream()
                .sorted((j1, j2) -> j1.getName().compareTo(j2.getName()))
                .collect(Collectors.toList());

        Map<String, String> testFailuresAnchors = new HashMap<>();
        for (GHWorkflowJob job : jobs) {
            if (!surefireReportsArtifactNames.contains(job.getName())) {
                continue;
            }
            testFailuresAnchors.put(job.getName(), "test-failures-job-" + job.getId());
        }

        StringBuilder sb = new StringBuilder();

        // Jobs report
        Optional<String> jobsAnalysis = jobReportsAnalyzer.getAnalysis(workflowRun, jobs, testFailuresAnchors);
        if (jobsAnalysis.isPresent()) {
            sb.append(jobsAnalysis.get());
        }

        // Test failure reports (we need to reconcile both but that will do for now)
        Optional<String> surefireReportsAnalysis = surefireReportsAnalyzer.getAnalysis(workflowRunPayload.getRepository(),
                pullRequest,
                surefireReportsArtifacts, testFailuresAnchors);

        if (surefireReportsAnalysis.isPresent()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(surefireReportsAnalysis.get());
        }

        if (sb.length() > 0) {
            sb.append("\n\n").append(MESSAGE_ID_ACTIVE);

            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.comment(sb.toString());
            } else {
                LOG.info("Workflow run #" + workflowRun.getId() + " - Add test failures:\n" + sb.toString());
            }
        }
    }
}
