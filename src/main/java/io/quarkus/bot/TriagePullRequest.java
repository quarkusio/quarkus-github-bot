package io.quarkus.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;

import com.hrakaroo.glob.GlobPattern;
import com.hrakaroo.glob.MatchingEngine;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.config.QuarkusBotConfigFile;
import io.quarkus.bot.config.QuarkusBotConfigFile.TriageRule;

class TriagePullRequest {

    private static final Logger LOG = Logger.getLogger(TriagePullRequest.class);

    /**
     * We cannot add more than 100 labels and we have some other automatic labels such as kind/bug.
     */
    private static final int LABEL_SIZE_LIMIT = 95;

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void triageIssue(
            @PullRequest.Opened @PullRequest.Edited @PullRequest.Synchronize GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-bot.yml") QuarkusBotConfigFile quarkusBotConfigFile) throws IOException {

        if (quarkusBotConfigFile == null) {
            LOG.error("Unable to find triage configuration.");
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        Set<String> labels = new TreeSet<>();
        Set<String> mentions = new TreeSet<>();

        for (TriageRule rule : quarkusBotConfigFile.triage.rules) {
            if (matchRule(pullRequest, rule)) {
                if (!rule.labels.isEmpty()) {
                    labels.addAll(rule.labels);
                }
                if (!rule.notify.isEmpty() && rule.notifyInPullRequest
                        && PullRequest.Opened.NAME.equals(pullRequestPayload.getAction())) {
                    for (String mention : rule.notify) {
                        if (!mention.equals(pullRequest.getUser().getLogin())) {
                            mentions.add(mention);
                        }
                    }
                }
            }
        }

        if (!labels.isEmpty()) {
            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.addLabels(limit(labels).toArray(new String[0]));
            } else {
                LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add labels: " + String.join(", ", limit(labels)));
            }
        }

        if (!mentions.isEmpty()) {
            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.comment("/cc @" + String.join(", @", mentions));
            } else {
                LOG.info("Pull Request #" + pullRequest.getNumber() + " - Mentions: " + String.join(", ", mentions));
            }
        }
    }

    private static Collection<String> limit(Set<String> labels) {
        if (labels.size() <= LABEL_SIZE_LIMIT) {
            return labels;
        }

        return new ArrayList<>(labels).subList(0, LABEL_SIZE_LIMIT);
    }

    private static boolean matchRule(GHPullRequest pullRequest, TriageRule rule) {
        // for now, we only use the files but we could also use the other rules at some point
        if (rule.directories.isEmpty()) {
            return false;
        }

        for (GHPullRequestFileDetail changedFile : pullRequest.listFiles()) {
            for (String directory : rule.directories) {
                if (!directory.contains("*")) {
                    if (changedFile.getFilename().startsWith(directory)) {
                        return true;
                    }
                } else {
                    try {
                        MatchingEngine matchingEngine = GlobPattern.compile(directory);
                        if (matchingEngine.matches(changedFile.getFilename())) {
                            return true;
                        }
                    } catch (Exception e) {
                        LOG.error("Error evaluating glob expression: " + directory, e);
                    }
                }
            }
        }

        return false;
    }
}
