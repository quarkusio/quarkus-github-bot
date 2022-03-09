package io.quarkus.bot;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;

import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.workflow.WorkflowConstants;

public class HideOutdatedWorkflowRunResults {

    private static final Logger LOG = Logger.getLogger(HideOutdatedWorkflowRunResults.class);

    private static final String HIDE_MESSAGE_PREFIX = "_This workflow status is outdated as a new workflow run has been triggered._\n"
            + "\n"
            + "<details>\n"
            + "\n";
    private static final String HIDE_MESSAGE_SUFFIX = "\n\n</details>";

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void hideOutdatedWorkflowRunResults(@WorkflowRun.Requested GHEventPayload.WorkflowRun workflowRunPayload)
            throws IOException {
        GHWorkflowRun workflowRun = workflowRunPayload.getWorkflowRun();
        GHWorkflow workflow = workflowRunPayload.getWorkflow();

        if (!WorkflowConstants.QUARKUS_CI_WORKFLOW_NAME.equals(workflow.getName())) {
            return;
        }
        if (workflowRun.getEvent() != GHEvent.PULL_REQUEST) {
            return;
        }

        List<GHPullRequest> pullRequests = workflowRun.getRepository().queryPullRequests()
                .state(GHIssueState.OPEN)
                .head(workflowRun.getHeadRepository().getOwnerName() + ":" + workflowRun.getHeadBranch())
                .list().toList();
        if (pullRequests.isEmpty()) {
            return;
        }

        hideOutdatedWorkflowRunResults(quarkusBotConfig, pullRequests.get(0));
    }

    static void hideOutdatedWorkflowRunResults(QuarkusGitHubBotConfig quarkusBotConfig, GHPullRequest pullRequest)
            throws IOException {
        List<GHIssueComment> comments = pullRequest.getComments();

        for (GHIssueComment comment : comments) {
            if (!comment.getBody().contains(WorkflowConstants.MESSAGE_ID_ACTIVE)) {
                continue;
            }

            StringBuilder updatedComment = new StringBuilder();
            updatedComment.append(HIDE_MESSAGE_PREFIX);
            updatedComment.append(comment.getBody().replace(WorkflowConstants.MESSAGE_ID_ACTIVE,
                    WorkflowConstants.MESSAGE_ID_HIDDEN));
            updatedComment.append(HIDE_MESSAGE_SUFFIX);

            if (!quarkusBotConfig.isDryRun()) {
                try {
                    comment.update(updatedComment.toString());
                } catch (IOException e) {
                    LOG.error(
                            "Unable to hide outdated workflow run status for comment " + comment.getId() + " of pull request #"
                                    + pullRequest.getNumber());
                }
            } else {
                LOG.info(
                        "Pull request #" + pullRequest.getNumber() + " - Hide outdated workflow run status " + comment.getId());
            }
        }
    }
}
