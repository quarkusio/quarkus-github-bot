package io.quarkus.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.Strings;

class CheckPullRequestEditorialRules {

    private static final Logger LOG = Logger.getLogger(CheckPullRequestEditorialRules.class);

    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern ISSUE_PATTERN = Pattern.compile("#[0-9]+");
    // for example, (3.2) or [3.2]
    private static final Pattern MAINTENANCE_BRANCH_PATTERN = Pattern.compile("^(\\[\\d+.\\d+\\]|\\(\\d+.\\d+\\)).*");
    private static final Pattern FIX_FEAT_CHORE = Pattern.compile("^(fix|chore|feat|docs|refactor)[(:].*");

    private static final List<String> UPPER_CASE_EXCEPTIONS = Arrays.asList("gRPC");

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void checkPullRequestEditorialRules(@PullRequest.Opened GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.CHECK_EDITORIAL_RULES.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        String title = pullRequest.getTitle();

        List<String> errorMessages = getErrorMessages(title);

        if (errorMessages.isEmpty()) {
            return;
        }

        StringBuilder comment = new StringBuilder("""
                Thanks for your pull request!

                The title of your pull request does not follow our editorial rules. Could you have a look?

                """);
        for (String errorMessage : errorMessages) {
            comment.append("- ").append(errorMessage).append("\n");
        }

        if (!quarkusBotConfig.isDryRun()) {
            pullRequest.comment(Strings.commentByBot(comment.toString()));
        } else {
            LOG.info("Pull request #" + pullRequest.getNumber() + " - Add comment " + comment.toString());
        }
    }

    private static List<String> getErrorMessages(String title) {
        List<String> errorMessages = new ArrayList<>();

        if (title == null || title.isEmpty()) {
            return Collections.singletonList("title should not be empty");
        }

        if (title.endsWith(".")) {
            errorMessages.add("title should not end up with dot");
        }
        if (title.endsWith("…")) {
            errorMessages.add("title should not end up with ellipsis (make sure the title is complete)");
        }
        if (SPACE_PATTERN.split(title.trim()).length < 2) {
            errorMessages.add("title should count at least 2 words to describe the change properly");
        }
        if (!Character.isDigit(title.codePointAt(0)) && !Character.isUpperCase(title.codePointAt(0))
                && !isUpperCaseException(title) && !MAINTENANCE_BRANCH_PATTERN.matcher(title).matches()) {
            errorMessages.add("title should preferably start with an uppercase character (if it makes sense!)");
        }
        if (ISSUE_PATTERN.matcher(title).find()) {
            errorMessages.add("title should not contain an issue number (use `Fix #1234` in the description instead)");
        }
        if (FIX_FEAT_CHORE.matcher(title).matches()) {
            errorMessages.add("title should not start with chore/docs/feat/fix/refactor but be a proper sentence");
        }

        return errorMessages;
    }

    private static boolean isUpperCaseException(String title) {
        for (String exception : UPPER_CASE_EXCEPTIONS) {
            if (title.toLowerCase(Locale.ROOT).startsWith(exception.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }
}
