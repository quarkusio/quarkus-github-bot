package io.quarkus.bot;

import java.io.IOException;

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
import io.quarkus.bot.util.Labels;

class MarkClosedPullRequestInvalid {

    private static final Logger LOG = Logger.getLogger(MarkClosedPullRequestInvalid.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void markClosedPullRequestInvalid(@PullRequest.Closed GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.QUARKUS_REPOSITORY_WORKFLOW.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();

        if (pullRequest.isMerged()) {
            return;
        }

        if (!quarkusBotConfig.isDryRun()) {
            pullRequest.addLabels(Labels.TRIAGE_INVALID);
        } else {
            LOG.info("Pull request #" + pullRequest.getNumber() + " - Add label: " + Labels.TRIAGE_INVALID);
        }

        for (GHLabel label : pullRequest.getLabels()) {
            if (label.getName().startsWith(Labels.TRIAGE_BACKPORT_PREFIX)) {
                if (!quarkusBotConfig.isDryRun()) {
                    pullRequest.removeLabel(label.getName());
                } else {
                    LOG.info("Pull request #" + pullRequest.getNumber() + " - Remove label: " + label.getName());
                }
            }
        }
    }
}
