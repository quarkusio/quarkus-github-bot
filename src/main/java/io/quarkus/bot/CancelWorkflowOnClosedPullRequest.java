package io.quarkus.bot;

import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.workflow.WorkflowConstants;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflowRun;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

class CancelWorkflowOnClosedPullRequest {
    private static final Logger LOG = Logger.getLogger(CancelWorkflowOnClosedPullRequest.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    public void onClose(@PullRequest.Closed GHEventPayload.PullRequest pullRequestPayload) throws IOException {

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();

        List<GHWorkflowRun> ghWorkflowRuns = pullRequest.getRepository()
                .queryWorkflowRuns()
                .branch(pullRequest.getHead().getRef())
                .list()
                .toList()
                .stream()
                .filter(workflowRun -> workflowRun.getHeadRepository().getOwnerName()
                        .equals(pullRequest.getHead().getRepository().getOwnerName()))
                .filter(workflowRun -> WorkflowConstants.QUARKUS_CI_WORKFLOW_NAME.equals(workflowRun.getName()) ||
                        WorkflowConstants.QUARKUS_DOCUMENTATION_CI_WORKFLOW_NAME.equals(workflowRun.getName()))
                .filter(workflowRun -> workflowRun.getStatus() == GHWorkflowRun.Status.QUEUED
                        || workflowRun.getStatus() == GHWorkflowRun.Status.IN_PROGRESS)
                .collect(Collectors.toList());

        for (GHWorkflowRun workflowRun : ghWorkflowRuns) {
            if (!quarkusBotConfig.isDryRun()) {
                workflowRun.cancel();
            } else {
                LOG.info("Workflow run #" + workflowRun.getId() + " - Cancelling as pull request #" + pullRequest.getNumber()
                        + " is now closed");
            }
        }
    }
}
