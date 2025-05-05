package io.quarkus.bot;

import static io.quarkus.bot.util.Strings.EDITORIAL_RULES_COMMENT_MARKER;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;

import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.util.Strings;

/**
 * This class handles all GitHub operations related to bot actions.
 * It provides methods for adding, updating, and removing comments on pull requests,
 * as well as setting pull request titles.
 */
@RequestScoped
class GitHubBotActions {

    private static final Logger LOG = Logger.getLogger(GitHubBotActions.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;


    public void addComment(GHPullRequest pullRequest, String comment, boolean isEditorialComment) {
        if (!quarkusBotConfig.isDryRun()) {
            try {
                String formattedComment = isEditorialComment ? Strings.editorialCommentByBot(comment)
                        : Strings.commentByBot(comment);
                pullRequest.comment(formattedComment);
                LOG.debug("Pull request #" + pullRequest.getNumber() + " - Added new comment");
            } catch (IOException e) {
                LOG.error(String.format("Pull Request #%s - Failed to add comment", pullRequest.getNumber()), e);
            }
        } else {
            LOG.info("Pull request #" + pullRequest.getNumber() + " - Add comment " + comment);
        }
    }


    public void updateComment(GHIssueComment comment, String newText, int pullRequestNumber, boolean isEditorialComment) {
        if (!quarkusBotConfig.isDryRun()) {
            try {
                String formattedComment = isEditorialComment ? Strings.editorialCommentByBot(newText)
                        : Strings.commentByBot(newText);
                comment.update(formattedComment);
                LOG.debug("Pull request #" + pullRequestNumber + " - Updated comment");
            } catch (IOException e) {
                LOG.error(String.format("Pull Request #%s - Failed to update comment %d",
                        pullRequestNumber, comment.getId()), e);
            }
        } else {
            LOG.info(String.format("Pull Request #%s - Update comment %d with: %s",
                    pullRequestNumber, comment.getId(), newText));
        }
    }


    public void deleteComment(GHIssueComment comment, int pullRequestNumber) {
        if (!quarkusBotConfig.isDryRun()) {
            try {
                comment.delete();
                LOG.debug("Pull request #" + pullRequestNumber + " - Deleted comment");
            } catch (IOException e) {
                LOG.error(String.format("Pull Request #%s - Failed to delete comment %d",
                        pullRequestNumber, comment.getId()), e);
            }
        } else {
            LOG.info(String.format("Pull Request #%s - Delete comment %d",
                    pullRequestNumber, comment.getId()));
        }
    }


    public void setPullRequestTitle(GHPullRequest pullRequest, String newTitle) {
        if (!quarkusBotConfig.isDryRun()) {
            try {
                pullRequest.setTitle(newTitle);
                LOG.debug("Pull request #" + pullRequest.getNumber() + " - Updated title to: " + newTitle);
            } catch (IOException e) {
                LOG.error(String.format("Pull Request #%s - Failed to update title", pullRequest.getNumber()), e);
            }
        } else {
            LOG.info("Pull request #" + pullRequest.getNumber() + " - Update title to: " + newTitle);
        }
    }


    public Optional<GHIssueComment> findBotComment(GHPullRequest pullRequest) throws IOException {
        List<GHIssueComment> comments = pullRequest.getComments();
        if (comments == null || comments.isEmpty()) {
            return Optional.empty();
        }

        return comments.stream()
                .filter(comment -> comment.getBody() != null)
                .filter(comment -> comment.getBody().contains(EDITORIAL_RULES_COMMENT_MARKER))
                .findFirst();
    }
}