package io.quarkus.bot;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.GHIssues;
import io.quarkus.bot.util.Labels;
import io.quarkus.bot.util.Mentions;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

public class NotifyQE {

    private static final Logger LOG = Logger.getLogger(NotifyQE.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void commentOnIssue(@Issue.Labeled GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.NOTIFY_QE.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        comment(quarkusBotConfigFile, issuePayload.getIssue(), issuePayload.getLabel(), gitHubGraphQLClient);
    }

    void commentOnPullRequest(@PullRequest.Labeled GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.NOTIFY_QE.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        comment(quarkusBotConfigFile, pullRequestPayload.getPullRequest(), pullRequestPayload.getLabel(), gitHubGraphQLClient);
    }

    private void comment(QuarkusGitHubBotConfigFile quarkusBotConfigFile, GHIssue issue, GHLabel label,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (quarkusBotConfigFile == null) {
            LOG.error("Unable to find triage configuration.");
            return;
        }

        if (label.getName().equals(Labels.TRIAGE_QE)) {
            if (!quarkusBotConfigFile.triage.qe.notify.isEmpty()) {
                if (!quarkusBotConfig.isDryRun()) {
                    Mentions mentions = new Mentions();
                    mentions.add(quarkusBotConfigFile.triage.qe.notify, "qe");
                    mentions.removeAlreadyParticipating(GHIssues.getParticipatingUsers(issue, gitHubGraphQLClient));

                    if (!mentions.isEmpty()) {
                        issue.comment("/cc " + mentions.getMentionsString());
                    }
                } else {
                    LOG.info((issue instanceof GHPullRequest ? "Pull Request #" : "Issue #") + issue.getNumber() +
                            " - Added label: " + Labels.TRIAGE_QE +
                            " - Mentioning QE: " + quarkusBotConfigFile.triage.qe.notify);
                }
            } else {
                LOG.warn("Added label: " + Labels.TRIAGE_QE + ", but no QE config is available");
            }
        }
    }
}
