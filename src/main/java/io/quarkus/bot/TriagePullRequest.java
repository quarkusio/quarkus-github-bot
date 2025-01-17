package io.quarkus.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile.GuardedBranch;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile.TriageRule;
import io.quarkus.bot.util.GHIssues;
import io.quarkus.bot.util.Mentions;
import io.quarkus.bot.util.Strings;
import io.quarkus.bot.util.Triage;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

class TriagePullRequest {

    private static final Logger LOG = Logger.getLogger(TriagePullRequest.class);

    private static final String BACKPORTS_BRANCH = "-backports-";

    /**
     * We cannot add more than 100 labels and we have some other automatic labels such as kind/bug.
     */
    private static final int LABEL_SIZE_LIMIT = 95;

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void triagePullRequest(
            @PullRequest.Opened @PullRequest.Edited @PullRequest.Synchronize GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.TRIAGE_ISSUES_AND_PULL_REQUESTS.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        Set<String> labels = new TreeSet<>();
        Mentions mentions = new Mentions();
        List<String> comments = new ArrayList<>();
        boolean isBackportsBranch = pullRequest.getHead().getRef().contains(BACKPORTS_BRANCH);
        // The second pass is allowed if either:
        // - no rule matched in the first pass
        // - OR all matching rules from the first pass explicitly allow the second pass
        boolean allowSecondPass = true;

        for (TriageRule rule : quarkusBotConfigFile.triage.rules) {
            if (Triage.matchRuleFromChangedFiles(pullRequest, rule)) {
                allowSecondPass = allowSecondPass && rule.allowSecondPass;
                applyRule(pullRequestPayload, pullRequest, isBackportsBranch, rule, labels, mentions, comments);
            }
        }

        if (allowSecondPass) {
            // Do a second pass, triaging according to the PR title/body
            for (TriageRule rule : quarkusBotConfigFile.triage.rules) {
                if (Triage.matchRuleFromDescription(pullRequest.getTitle(), pullRequest.getBody(), rule)) {
                    applyRule(pullRequestPayload, pullRequest, isBackportsBranch, rule, labels, mentions, comments);
                }
            }
        }

        for (GuardedBranch guardedBranch : quarkusBotConfigFile.triage.guardedBranches) {
            if (guardedBranch.ref.equals(pullRequest.getBase().getRef())) {
                for (String mention : guardedBranch.notify) {
                    mentions.add(mention, guardedBranch.ref);
                }
            }
        }

        // remove from the set the labels already present on the pull request
        if (!labels.isEmpty()) {
            pullRequest.getLabels().stream().map(GHLabel::getName).forEach(labels::remove);

            if (!labels.isEmpty()) {
                if (!quarkusBotConfig.isDryRun()) {
                    pullRequest.addLabels(limit(labels).toArray(new String[0]));
                } else {
                    LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add labels: " + String.join(", ", limit(labels)));
                }
            }
        }

        if (!mentions.isEmpty()) {
            mentions.removeAlreadyParticipating(GHIssues.getParticipatingUsers(pullRequest, gitHubGraphQLClient));
            if (!mentions.isEmpty()) {
                comments.add("/cc " + mentions.getMentionsString());
            }
        }

        for (String comment : comments) {
            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.comment(comment);
            } else {
                LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add comment: " + comment);
            }
        }
    }

    private void applyRule(GHEventPayload.PullRequest pullRequestPayload, GHPullRequest pullRequest, boolean isBackportsBranch,
            TriageRule rule, Set<String> labels, Mentions mentions, List<String> comments) throws IOException {
        if (!rule.labels.isEmpty()) {
            labels.addAll(rule.labels);
        }

        if (!rule.notify.isEmpty() && rule.notifyInPullRequest
                && PullRequest.Opened.NAME.equals(pullRequestPayload.getAction())
                && !isBackportsBranch) {
            for (String mention : rule.notify) {
                if (!mention.equals(pullRequest.getUser().getLogin())) {
                    mentions.add(mention, rule.id);
                }
            }
        }
        if (Strings.isNotBlank(rule.comment)) {
            comments.add(rule.comment);
        }
    }

    private static Collection<String> limit(Set<String> labels) {
        if (labels.size() <= LABEL_SIZE_LIMIT) {
            return labels;
        }

        return new ArrayList<>(labels).subList(0, LABEL_SIZE_LIMIT);
    }

}
