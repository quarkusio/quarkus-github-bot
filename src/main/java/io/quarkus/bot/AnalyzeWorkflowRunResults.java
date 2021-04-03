package io.quarkus.bot;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.maven.reporting.MavenReportException;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.workflow.SurefireReportsAnalyzer;

public class AnalyzeWorkflowRunResults {

    private static final Logger LOG = Logger.getLogger(AnalyzeWorkflowRunResults.class);

    public static final String SUREFIRE_REPORTS_ARTIFACT_PREFIX = "surefire-reports-";
    private static final String PULL_REQUEST_NUMBER_PREFIX = "pull-request-number-";
    private static final String QUARKUS_CI_WORKFLOW_NAME = "Quarkus CI";

    @Inject
    SurefireReportsAnalyzer surefireReportsAnalyzer;

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void analyzeWorkflowResults(@WorkflowRun.Completed GHEventPayload.WorkflowRun workflowRunPayload)
            throws IOException, MavenReportException {
        GHWorkflowRun workflowRun = workflowRunPayload.getWorkflowRun();
        GHWorkflow workflow = workflowRunPayload.getWorkflow();

        if (!QUARKUS_CI_WORKFLOW_NAME.equals(workflow.getName())) {
            return;
        }
        if (workflowRun.getConclusion() != Conclusion.FAILURE) {
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

        if (artifacts.isEmpty()) {
            return;
        }

        Optional<String> analysis = surefireReportsAnalyzer.getAnalysis(workflowRunPayload.getRepository(), pullRequest, surefireReportsArtifacts);

        if (analysis.isPresent()) {
            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.comment(analysis.get());
            } else {
                LOG.info("Workflow run #" + workflowRun.getId() + " - Add test failures:\n" + analysis.get());
            }
        }
    }
}
