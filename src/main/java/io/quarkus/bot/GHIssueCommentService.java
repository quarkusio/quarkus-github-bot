package io.quarkus.bot;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;

import io.quarkus.bot.util.Strings;

@Singleton
public class GHIssueCommentService {
    private static final Logger LOG = Logger.getLogger(GHIssueCommentService.class);

    public void updateComment(GHIssueComment comment, String newText, int issueNumber, boolean isEditorialComment,
            boolean isDryRun) {
        if (!isDryRun) {
            try {
                String formattedComment = isEditorialComment ? Strings.editorialCommentByBot(newText)
                        : Strings.commentByBot(newText);
                comment.update(formattedComment);
                LOG.debug("Pull request #" + issueNumber + " - Updated comment");
            } catch (IOException e) {
                LOG.error(String.format("Pull Request #%s - Failed to update comment %d",
                        issueNumber, comment.getId()), e);
            }
        } else {
            LOG.info(String.format("Pull Request #%s - Update comment %d with: %s",
                    issueNumber, comment.getId(), newText));
        }
    }

    public void deleteComment(GHIssueComment comment, int issueNumber, boolean isDryRun) {
        if (!isDryRun) {
            try {
                comment.delete();
                LOG.debug("Pull request #" + issueNumber + " - Deleted comment");
            } catch (IOException e) {
                LOG.error(String.format("Pull Request #%s - Failed to delete comment %d",
                        issueNumber, comment.getId()), e);
            }
        } else {
            LOG.info(String.format("Pull Request #%s - Delete comment %d",
                    issueNumber, comment.getId()));
        }
    }

    public Optional<GHIssueComment> findBotCommentInIssue(GHIssue GhIssue, String marker) throws IOException {
        List<GHIssueComment> comments = GhIssue.getComments();
        if (comments == null || comments.isEmpty()) {
            return Optional.empty();
        }

        return comments.stream()
                .filter(comment -> comment.getBody() != null)
                .filter(comment -> comment.getBody().contains(marker))
                .findFirst();
    }

    public void addComment(GHIssue ghIssue, String comment, boolean isEditorialComment, boolean isDryRun) {
        if (!isDryRun) {
            try {
                String formattedComment = isEditorialComment ? Strings.editorialCommentByBot(comment)
                        : Strings.commentByBot(comment);
                ghIssue.comment(formattedComment);
                LOG.debugf("Pull request #%d - Added new comment", ghIssue.getNumber());
            } catch (IOException e) {
                LOG.errorf(e, "Pull Request #%d - Failed to add comment", ghIssue.getNumber());
            }
        } else {
            LOG.infof("Pull request #%d - Add comment (dry-run): %s", ghIssue.getNumber(), comment);
        }
    }
}
