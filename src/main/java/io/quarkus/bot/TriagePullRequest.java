package io.quarkus.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile.TriageRule;
import io.quarkus.bot.util.Strings;
import io.quarkus.bot.util.Triage;

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
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.TRIAGE_ISSUES_AND_PULL_REQUESTS.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        if (quarkusBotConfigFile.triage.rules.isEmpty()) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        Set<String> labels = new TreeSet<>();
        Set<String> mentions = new TreeSet<>();
        List<String> comments = new ArrayList<>();
        boolean isBackportsBranch = pullRequest.getHead().getRef().contains(BACKPORTS_BRANCH);
        boolean atLeastOneMatch = false;

        for (TriageRule rule : quarkusBotConfigFile.triage.rules) {
            if (Triage.matchRuleFromChangedFiles(pullRequest, rule)) {
                atLeastOneMatch = true;
                applyRule(pullRequestPayload, pullRequest, isBackportsBranch, rule, labels, mentions, comments);
            }
        }

        if (!atLeastOneMatch) {
            // Fall back to triaging according to the PR title/body
            for (TriageRule rule : quarkusBotConfigFile.triage.rules) {
                if (Triage.matchRuleFromDescription(pullRequest.getTitle(), pullRequest.getBody(), rule)) {
                    applyRule(pullRequestPayload, pullRequest, isBackportsBranch, rule, labels, mentions, comments);
                }
            }
        }

        // remove from the set the labels already present on the pull request
        pullRequest.getLabels().stream().map(GHLabel::getName).forEach(labels::remove);

        if (!labels.isEmpty()) {
            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.addLabels(limit(labels).toArray(new String[0]));
            } else {
                LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add labels: " + String.join(", ", limit(labels)));
            }
        }

        if (!mentions.isEmpty()) {
            comments.add("/cc @" + String.join(", @", mentions));
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
            TriageRule rule, Set<String> labels, Set<String> mentions, List<String> comments) throws IOException {
        if (!rule.labels.isEmpty()) {
            labels.addAll(rule.labels);
        }

        if (!rule.notify.isEmpty() && rule.notifyInPullRequest
                && PullRequest.Opened.NAME.equals(pullRequestPayload.getAction())
                && !isBackportsBranch) {
            for (String mention : rule.notify) {
                if (!mention.equals(pullRequest.getUser().getLogin())) {
                    mentions.add(mention);
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
