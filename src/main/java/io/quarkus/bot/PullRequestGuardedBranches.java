package io.quarkus.bot;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile.GuardedBranch;
import io.quarkus.bot.util.GHIssues;
import io.quarkus.bot.util.Mentions;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

class PullRequestGuardedBranches {

    private static final Logger LOG = Logger.getLogger(PullRequestGuardedBranches.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void triagePullRequest(
            @PullRequest.Opened GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.TRIAGE_ISSUES_AND_PULL_REQUESTS.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        Mentions mentions = new Mentions();

        for (GuardedBranch guardedBranch : quarkusBotConfigFile.triage.guardedBranches) {
            if (guardedBranch.ref.equals(pullRequest.getBase().getRef())) {
                for (String mention : guardedBranch.notify) {
                    mentions.add(mention, guardedBranch.ref);
                }
            }
        }

        if (mentions.isEmpty()) {
            return;
        }

        mentions.removeAlreadyParticipating(GHIssues.getParticipatingUsers(pullRequest, gitHubGraphQLClient));

        if (mentions.isEmpty()) {
            return;
        }

        String comment = "/cc " + mentions.getMentionsString();
        if (!quarkusBotConfig.isDryRun()) {
            pullRequest.comment(comment);
        } else {
            LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add comment: " + comment);
        }
    }
}
