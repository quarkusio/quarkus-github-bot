package io.quarkus.bot;

import static io.quarkus.bot.util.Strings.EDITORIAL_RULES_COMMENT_MARKER;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.GHPullRequests;
import io.quarkus.bot.violation.EditorialViolation;
import io.quarkus.bot.violation.ViolationDetectorManager;

class CheckPullRequestEditorialRules {
    private static final Logger LOG = Logger.getLogger(CheckPullRequestEditorialRules.class);

    public static final String HEADER = """
            Thanks for your pull request!

            Your pull request does not follow our editorial rules. Could you have a look?

            """;

    @Inject
    GitHubBotActions gitHubBotActions;

    @Inject
    ViolationDetectorManager violationManager;

    void checkPullRequestEditorialRules(@PullRequest.Opened GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.CHECK_EDITORIAL_RULES.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        String baseBranch = pullRequestPayload.getPullRequest().getBase().getRef();

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        normalizeTitle(pullRequest, baseBranch);

        processViolations(pullRequest, false);
    }

    void checkPullRequestEditorialRulesOnEdit(@PullRequest.Edited GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.CHECK_EDITORIAL_RULES.isEnabled(quarkusBotConfigFile)) {
            return;
        }
        processViolations(pullRequestPayload.getPullRequest(), true);
    }

    private void normalizeTitle(GHPullRequest pullRequest, String baseBranch) {
        String originalTitle = pullRequest.getTitle();
        String normalizedTitle = GHPullRequests.normalizeTitle(originalTitle, baseBranch);

        if (!originalTitle.equals(normalizedTitle)) {
            gitHubBotActions.setPullRequestTitle(pullRequest, normalizedTitle);
        }
    }

    private void processViolations(GHPullRequest pullRequest, boolean isEdit) throws IOException {
        List<EditorialViolation> violations = violationManager.detectViolations(pullRequest);
        List<String> titleErrorMessages = violationManager.getTitleViolationMessages(violations);
        List<String> bodyErrorMessages = violationManager.getBodyViolationMessages(violations);

        if (titleErrorMessages.isEmpty() && bodyErrorMessages.isEmpty()) {
            // No violations - remove existing comment if this is an edit
            if (isEdit) {
                gitHubBotActions.findBotComment(pullRequest, EDITORIAL_RULES_COMMENT_MARKER)
                        .ifPresent(comment -> gitHubBotActions.deleteComment(comment, pullRequest.getNumber()));
            }
            return;
        }

        String violationComment = buildViolationComment(titleErrorMessages, bodyErrorMessages);

        if (isEdit) {
            processCommentUpdate(pullRequest, EDITORIAL_RULES_COMMENT_MARKER, violationComment, true);
        } else {
            gitHubBotActions.addComment(pullRequest, violationComment, true);
        }
    }

    private String buildViolationComment(List<String> titleErrors, List<String> bodyErrors) {
        StringJoiner comment = new StringJoiner("\n", HEADER, "");
        titleErrors.forEach(error -> comment.add("- " + error));
        bodyErrors.forEach(error -> comment.add("- " + error));
        return comment.toString();
    }

    public void processCommentUpdate(GHPullRequest pr, String marker, String newContent, boolean isEditorial) {
        try {
            Optional<GHIssueComment> existingComment = gitHubBotActions.findBotComment(pr, marker);
            if (existingComment.isPresent()) {
                gitHubBotActions.updateComment(existingComment.get(), newContent, pr.getNumber(), isEditorial);
            } else {
                gitHubBotActions.addComment(pr, newContent, isEditorial);
            }
        } catch (IOException e) {
            LOG.error("Failed to process comment update for PR #" + pr.getNumber(), e);
        }
    }

}
