package io.quarkus.bot;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.GHIssues;
import io.quarkus.bot.util.Labels;
import io.quarkus.bot.util.Mentions;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

public class PingWhenNeedsTriageRemoved {
    private static final Logger LOG = Logger.getLogger(PingWhenNeedsTriageRemoved.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void pingWhenNeedsTriageRemoved(@Issue.Unlabeled GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.TRIAGE_ISSUES_AND_PULL_REQUESTS.isEnabled(quarkusBotConfigFile)) {
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

        Mentions mentions = new Mentions();

        for (QuarkusGitHubBotConfigFile.TriageRule rule : quarkusBotConfigFile.triage.rules) {
            if (matchRule(issue, rule)) {
                if (!rule.notify.isEmpty()) {
                    for (String mention : rule.notify) {
                        if (mention.equals(issue.getUser().getLogin()) || mention.equals(issuePayload.getSender().getLogin())) {
                            continue;
                        }
                        mentions.add(mention, rule.id);
                    }
                }
            }
        }

        mentions.removeAlreadyParticipating(GHIssues.getParticipatingUsers(issue, gitHubGraphQLClient));

        if (mentions.isEmpty()) {
            return;
        }

        if (!quarkusBotConfig.isDryRun()) {
            issue.comment("/cc " + mentions.getMentionsString());
        } else {
            LOG.info("Issue #" + issue.getNumber() + " - Ping: " + mentions.getMentionsString());
        }
    }

    private static boolean matchRule(GHIssue issue, QuarkusGitHubBotConfigFile.TriageRule rule) {
        if (rule.labels.isEmpty() || rule.notify.isEmpty()) {
            return false;
        }

        return issue.getLabels().stream().anyMatch(l -> rule.labels.contains(l.getName()));
    }
}
