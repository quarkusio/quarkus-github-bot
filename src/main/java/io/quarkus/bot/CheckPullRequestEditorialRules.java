package io.quarkus.bot;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
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
        String originalTitle = pullRequest.getTitle();
        String normalizedTitle = GHPullRequests.normalizeTitle(originalTitle, baseBranch);

        if (!originalTitle.equals(normalizedTitle)) {
            gitHubBotActions.setPullRequestTitle(pullRequest, normalizedTitle);
        }

        List<EditorialViolation> violations = violationManager.detectViolations(pullRequest);
        List<String> titleErrorMessages = violationManager.getTitleViolationMessages(violations);
        List<String> bodyErrorMessages = violationManager.getBodyViolationMessages(violations);

        if (titleErrorMessages.isEmpty() && bodyErrorMessages.isEmpty()) {
            return;
        }
        StringJoiner comment = new StringJoiner("\n", HEADER, StringUtils.EMPTY);
        for (String errorMessage : titleErrorMessages) {
            comment.add("- " + errorMessage);
        }
        for (String errorMessage : bodyErrorMessages) {
            comment.add("- " + errorMessage);
        }

        gitHubBotActions.addComment(pullRequest, comment.toString(), true);
    }


    void checkPullRequestEditorialRulesOnEdit(@PullRequest.Edited GHEventPayload.PullRequest pullRequestPayload,
                                              @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.CHECK_EDITORIAL_RULES.isEnabled(quarkusBotConfigFile)) {
            return;
        }
        var pullRequest = pullRequestPayload.getPullRequest();

        // Detect violations using the violation manager
        List<EditorialViolation> violations = violationManager.detectViolations(pullRequest);
        var titleErrorMessages = violationManager.getTitleViolationMessages(violations);
        var bodyErrorMessages = violationManager.getBodyViolationMessages(violations);

        if (titleErrorMessages.isEmpty() && bodyErrorMessages.isEmpty()) {
            Optional<GHIssueComment> commentToDeleteOpt = gitHubBotActions.findBotComment(pullRequest);
            commentToDeleteOpt
                    .ifPresent(ghIssueComment -> gitHubBotActions.deleteComment(ghIssueComment, pullRequest.getNumber()));
        } else {
            Optional<GHIssueComment> existingCommentOpt = gitHubBotActions.findBotComment(pullRequest);

            StringJoiner comment = new StringJoiner("\n", HEADER, "");
            for (String errorMessage : titleErrorMessages) {
                comment.add("- " + errorMessage);
            }
            for (String errorMessage : bodyErrorMessages) {
                comment.add("- " + errorMessage);
            }

            if (existingCommentOpt.isPresent()) {
                gitHubBotActions.updateComment(existingCommentOpt.get(), comment.toString(), pullRequest.getNumber(), true);
            } else {
                gitHubBotActions.addComment(pullRequest, comment.toString(), true);
            }
        }
    }

}
