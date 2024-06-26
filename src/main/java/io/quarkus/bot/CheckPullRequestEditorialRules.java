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
import io.quarkus.bot.util.GHPullRequests;
import io.quarkus.bot.util.PullRequestFilesMatcher;
import io.quarkus.bot.util.Strings;

class CheckPullRequestEditorialRules {

    private static final Logger LOG = Logger.getLogger(CheckPullRequestEditorialRules.class);

    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern ISSUE_PATTERN = Pattern.compile("#[0-9]+");
    private static final Pattern FIX_FEAT_CHORE = Pattern.compile("^(fix|chore|feat|docs|refactor)[(:].*");

    private static final List<String> UPPER_CASE_EXCEPTIONS = Arrays.asList("gRPC");
    private static final List<String> BOMS = List.of("bom/application/pom.xml");
    private static final List<String> DOC_CHANGES = List.of("docs/src/main/asciidoc/", "README.md", "LICENSE",
            "CONTRIBUTING.md");

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void checkPullRequestEditorialRules(@PullRequest.Opened GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.CHECK_EDITORIAL_RULES.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        String baseBranch = pullRequestPayload.getPullRequest().getBase().getRef();

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        String body = pullRequest.getBody();
        String originalTitle = pullRequest.getTitle();
        String normalizedTitle = GHPullRequests.normalizeTitle(originalTitle, baseBranch);

        if (!originalTitle.equals(normalizedTitle)) {
            pullRequest.setTitle(normalizedTitle);
        }

        // we remove the potential version prefix before checking the editorial rules
        String title = GHPullRequests.dropVersionSuffix(normalizedTitle, baseBranch);

        List<String> titleErrorMessages = getTitleErrorMessages(title);
        List<String> bodyErrorMessages = getBodyErrorMessages(body, pullRequest);

        if (titleErrorMessages.isEmpty() && bodyErrorMessages.isEmpty()) {
            return;
        }

        StringBuilder comment = new StringBuilder("""
                Thanks for your pull request!

                Your pull request does not follow our editorial rules. Could you have a look?

                """);
        for (String errorMessage : titleErrorMessages) {
            comment.append("- ").append(errorMessage).append("\n");
        }
        for (String errorMessage : bodyErrorMessages) {
            comment.append("- ").append(errorMessage).append("\n");
        }

        if (!quarkusBotConfig.isDryRun()) {
            pullRequest.comment(Strings.commentByBot(comment.toString()));
        } else {
            LOG.info("Pull request #" + pullRequest.getNumber() + " - Add comment " + comment.toString());
        }
    }

    private static List<String> getTitleErrorMessages(String title) {
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
                && !isUpperCaseException(title)) {
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

    private static List<String> getBodyErrorMessages(String body, GHPullRequest pullRequest) throws IOException {
        List<String> errorMessages = new ArrayList<>();

        if ((body == null || body.isBlank()) && isMeaningfulPullRequest(pullRequest)) {
            return List.of(
                    "description should not be empty, describe your intent or provide links to the issues this PR is fixing (using `Fixes #NNNNN`) or changelogs");
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

    private static boolean isMeaningfulPullRequest(GHPullRequest pullRequest) throws IOException {
        // Note: these rules will have to be adjusted depending on how it goes
        // we don't want to annoy people fixing a typo or require a description for a one liner explained in the title

        // if we have more than one commit, then it's meaningful
        if (pullRequest.getCommits() > 1) {
            return true;
        }

        PullRequestFilesMatcher filesMatcher = new PullRequestFilesMatcher(pullRequest);

        // for changes to the BOM, we are stricter
        if (filesMatcher.changedFilesMatch(BOMS)) {
            return true;
        }

        // for one liner/two liners, let's be a little more lenient
        if (pullRequest.getAdditions() <= 2 && pullRequest.getDeletions() <= 2) {
            return false;
        }

        // let's be a little more flexible for doc changes
        if (filesMatcher.changedFilesOnlyMatch(DOC_CHANGES)
                && pullRequest.getAdditions() <= 10 && pullRequest.getDeletions() <= 10) {
            return false;
        }

        return true;
    }
}
