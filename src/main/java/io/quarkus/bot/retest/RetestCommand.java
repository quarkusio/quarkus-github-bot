package io.quarkus.bot.retest;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflowRun;

import com.github.rvesse.airline.annotations.Command;

import io.quarkiverse.githubapp.command.airline.CommandOptions;
import io.quarkiverse.githubapp.command.airline.CommandOptions.CommandScope;
import io.quarkiverse.githubapp.command.airline.CommandOptions.ExecutionErrorStrategy;
import io.quarkiverse.githubapp.command.airline.CommandOptions.ReactionStrategy;
import io.quarkiverse.githubapp.command.airline.Permission;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.service.GHIssueCommentService;

/**
 * Handles {@code @quarkusbot retest} comments on pull requests.
 */
@Command(name = "retest")
@Permission(GHPermissionType.WRITE)
@CommandOptions(scope = CommandScope.PULL_REQUESTS, executionErrorStrategy = ExecutionErrorStrategy.COMMENT_MESSAGE, executionErrorHandler = RetestExecutionErrorHandler.class, reactionStrategy = ReactionStrategy.NONE)
class RetestCommand implements RetestCommandHandler {

    private static final Logger LOG = Logger.getLogger(RetestCommand.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    @Inject
    RetestWorkflowRunSelector workflowRunSelector;

    @Inject
    FailedJobsRerunner failedJobsRerunner;

    @Inject
    GHIssueCommentService issueCommentService;

    @Override
    public void run(QuarkusGitHubBotConfigFile quarkusBotConfigFile, GHEventPayload.IssueComment issueCommentPayload) {
        if (!Feature.RETEST_PULL_REQUEST_WORKFLOWS.isEnabled(quarkusBotConfigFile)) {
            throw RetestCommandException.featureDisabled();
        }

        GHPullRequest pullRequest = getPullRequest(issueCommentPayload);
        if (pullRequest.getState() != GHIssueState.OPEN) {
            throw RetestCommandException.pullRequestNotOpen();
        }

        RetestWorkflowSelection workflowSelection = getWorkflowSelection(pullRequest);

        if (workflowSelection.eligibleRuns().isEmpty()) {
            throw RetestCommandException.noEligibleWorkflowRuns(workflowSelection.noEligibleReason());
        }

        List<GHWorkflowRun> startedWorkflowRuns = new ArrayList<>();
        for (GHWorkflowRun workflowRun : workflowSelection.eligibleRuns()) {
            if (quarkusBotConfig.isDryRun()) {
                LOG.infof("Pull request #%d - Retest failed jobs for workflow run #%d (dry-run)",
                        pullRequest.getNumber(), workflowRun.getId());
                continue;
            }

            try {
                failedJobsRerunner.rerunFailedJobs(issueCommentPayload, workflowRun);
                startedWorkflowRuns.add(workflowRun);
            } catch (RuntimeException e) {
                if (startedWorkflowRuns.isEmpty()) {
                    throw e;
                }

                throw RetestCommandException.partialRerunFailure(
                        startedWorkflowRuns.stream().map(GHWorkflowRun::getId).toList(), workflowRun.getId(), e);
            }
        }

        if (!startedWorkflowRuns.isEmpty()) {
            issueCommentService.addComment(issueCommentPayload.getIssue(), successMessage(startedWorkflowRuns), false,
                    quarkusBotConfig.isDryRun());
        }
    }

    private static GHPullRequest getPullRequest(GHEventPayload.IssueComment issueCommentPayload) {
        try {
            return issueCommentPayload.getRepository()
                    .getPullRequest(issueCommentPayload.getIssue().getNumber());
        } catch (IOException e) {
            throw RetestCommandException.unableToInspectWorkflowRuns(e);
        }
    }

    private RetestWorkflowSelection getWorkflowSelection(GHPullRequest pullRequest) {
        try {
            return workflowRunSelector.selectWorkflowRuns(pullRequest);
        } catch (IOException e) {
            throw RetestCommandException.unableToInspectWorkflowRuns(e);
        }
    }

    private static String successMessage(List<GHWorkflowRun> startedWorkflowRuns) {
        String workflowRunLinks = startedWorkflowRuns.stream()
                .map(RetestCommand::workflowRunReference)
                .collect(Collectors.joining(", "));
        String label = startedWorkflowRuns.size() == 1 ? "workflow run " : "workflow runs ";
        return ":arrows_counterclockwise: Retest started for failed jobs in " + label + workflowRunLinks + ".";
    }

    private static String workflowRunReference(GHWorkflowRun workflowRun) {
        String label = "#" + workflowRun.getId();
        URL htmlUrl;
        try {
            htmlUrl = workflowRun.getHtmlUrl();
        } catch (IOException e) {
            return label;
        }
        if (htmlUrl == null) {
            return label;
        }

        return "[" + label + "](" + htmlUrl + ")";
    }
}
