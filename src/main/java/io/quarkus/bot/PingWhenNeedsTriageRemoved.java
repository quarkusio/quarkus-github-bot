package io.quarkus.bot;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.config.QuarkusBotConfigFile;
import io.quarkus.bot.util.Labels;
import io.quarkus.bot.util.Patterns;
import io.quarkus.bot.util.Strings;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

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

        if (!removedLabel.toString().equals(Labels.TRIAGE_NEEDS_TRIAGE)) {
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

        if (removedLabel.getName().equals(Labels.TRIAGE_NEEDS_TRIAGE)) {
            if (!mentions.isEmpty()) {
                if (!quarkusBotConfig.isDryRun()) {
                    issue.comment("/cc @" + String.join(", @", mentions));
                } else {
                    LOG.info("Issue #" + issue.getNumber() + " - Ping: " + mentions);
                }
            }
        }
    }

    private static boolean matchRule(GHIssue issue, QuarkusBotConfigFile.TriageRule rule) {

        try {
            if (Strings.isNotBlank(rule.labels.toString())) {
                if (Patterns.find(rule.labels.toString(), issue.getLabels().toString())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error evaluating labels: " + rule.labels, e);
        }

        return false;
    }
}
