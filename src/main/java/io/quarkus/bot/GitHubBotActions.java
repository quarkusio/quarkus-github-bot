package io.quarkus.bot;

import java.io.IOException;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;

import io.quarkus.bot.config.QuarkusGitHubBotConfig;

/**
 * This class handles all GitHub operations related to bot actions.
 * It provides methods for adding, updating, and removing comments on pull requests,
 * as well as setting pull request titles.
 */
@Singleton
class GitHubBotActions {
    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;
    @Inject
    GHIssueService issueService;
    @Inject
    GHIssueCommentService commentService;

    public void addComment(GHPullRequest pullRequest, String comment, boolean isEditorialComment) {
        commentService.addComment(pullRequest, comment, isEditorialComment, quarkusBotConfig.isDryRun());
    }

    public void updateComment(GHIssueComment comment, String newText, int pullRequestNumber, boolean isEditorialComment) {
        commentService.updateComment(comment, newText, pullRequestNumber, isEditorialComment, quarkusBotConfig.isDryRun());
    }

    public void deleteComment(GHIssueComment comment, int pullRequestNumber) {
        commentService.deleteComment(comment, pullRequestNumber, quarkusBotConfig.isDryRun());
    }

    public void setPullRequestTitle(GHPullRequest pullRequest, String newTitle) {
        issueService.setIssueTitle(pullRequest, newTitle, quarkusBotConfig.isDryRun());
    }

    public Optional<GHIssueComment> findBotComment(GHPullRequest pullRequest, String marker) throws IOException {
        return commentService.findBotCommentInIssue(pullRequest, marker);
    }
}
