package io.quarkus.bot;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.config.QuarkusBotConfigFile;
import io.quarkus.bot.util.Labels;

public class PingWhenNeedsTriageRemoved {
    private static final Logger LOG = Logger.getLogger(PingWhenNeedsTriageRemoved.class);

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void pingWhenNeedsTriageRemoved(@Issue.Unlabeled GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-bot.yml") QuarkusBotConfigFile quarkusBotConfigFile) throws IOException {

        if (quarkusBotConfigFile == null) {
            LOG.error("Unable to find triage configuration.");
            return;
        }

        GHIssue issue = issuePayload.getIssue();
        GHLabel removedLabel = issuePayload.getLabel();

        if (!Labels.TRIAGE_NEEDS_TRIAGE.equals(removedLabel.getName())) {
            return;
        }
        if (issue.getLabels().isEmpty()) {
            return;
        }

        Set<String> mentions = new TreeSet<>();

        for (QuarkusBotConfigFile.TriageRule rule : quarkusBotConfigFile.triage.rules) {
            if (matchRule(issue, rule)) {
                if (!rule.notify.isEmpty()) {
                    for (String mention : rule.notify) {
                        if (!mention.equals(issue.getUser().getLogin())) {
                            mentions.add(mention);
                        }
                    }
                }
            }
        }

        if (mentions.isEmpty()) {
            return;
        }

        if (!quarkusBotConfig.isDryRun()) {
            issue.comment("/cc @" + String.join(", @", mentions));
        } else {
            LOG.info("Issue #" + issue.getNumber() + " - Ping: " + mentions);
        }
    }

    private static boolean matchRule(GHIssue issue, QuarkusBotConfigFile.TriageRule rule) {
        if (rule.labels.isEmpty() || rule.notify.isEmpty()) {
            return false;
        }

        return issue.getLabels().stream().anyMatch(l -> rule.labels.contains(l.getName()));
    }
}
