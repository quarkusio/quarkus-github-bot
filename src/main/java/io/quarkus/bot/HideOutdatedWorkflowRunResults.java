package io.quarkus.bot;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.workflow.WorkflowConstants;
import io.quarkus.bot.workflow.WorkflowContext;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

public class HideOutdatedWorkflowRunResults {

    private static final Logger LOG = Logger.getLogger(HideOutdatedWorkflowRunResults.class);

    private static final String HIDE_MESSAGE_PREFIX = """
            ---
            > :waning_crescent_moon: **_This workflow status is outdated as a new workflow run has been triggered._**
            ---

            """;
    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void hideOutdatedWorkflowRunResults(@WorkflowRun.Requested GHEventPayload.WorkflowRun workflowRunPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.ANALYZE_WORKFLOW_RUN_RESULTS.isEnabled(quarkusBotConfigFile)) {
            return;
        }
        if (quarkusBotConfigFile.workflowRunAnalysis.workflows == null ||
                quarkusBotConfigFile.workflowRunAnalysis.workflows.isEmpty()) {
            return;
        }

        GHWorkflowRun workflowRun = workflowRunPayload.getWorkflowRun();
        GHWorkflow workflow = workflowRunPayload.getWorkflow();

        if (!quarkusBotConfigFile.workflowRunAnalysis.workflows.contains(workflow.getName())) {
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

        hideOutdatedWorkflowRunResults(quarkusBotConfig, new WorkflowContext(pullRequests.get(0)), pullRequests.get(0),
                gitHubGraphQLClient);
    }

    static void hideOutdatedWorkflowRunResults(QuarkusGitHubBotConfig quarkusBotConfig,
            WorkflowContext workflowContext, GHIssue issue,
            DynamicGraphQLClient gitHubGraphQLClient)
            throws IOException {
        List<GHIssueComment> comments = issue.getComments();

        for (GHIssueComment comment : comments) {
            if (!comment.getBody().contains(WorkflowConstants.MESSAGE_ID_ACTIVE)) {
                continue;
            }

            StringBuilder updatedComment = new StringBuilder();
            updatedComment.append(HIDE_MESSAGE_PREFIX);
            updatedComment.append(comment.getBody().replace(WorkflowConstants.MESSAGE_ID_ACTIVE,
                    WorkflowConstants.MESSAGE_ID_HIDDEN));

            if (!quarkusBotConfig.isDryRun()) {
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

    static void minimizeOutdatedComment(DynamicGraphQLClient gitHubGraphQLClient, GHIssueComment comment)
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
}
