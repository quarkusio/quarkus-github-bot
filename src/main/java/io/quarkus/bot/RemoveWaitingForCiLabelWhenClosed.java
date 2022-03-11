package io.quarkus.bot;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.Labels;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collection;

public class RemoveWaitingForCiLabelWhenClosed {

    private static final Logger LOG = Logger.getLogger(RemoveWaitingForCiLabelWhenClosed.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void removeWaitingForCiLabelWhenClosed(@PullRequest.Closed GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.QUARKUS_REPOSITORY_WORKFLOW.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        Collection<GHLabel> labels = pullRequest.getLabels();

        for (GHLabel label : labels) {
            if (label.getName().equals(Labels.TRIAGE_WAITING_FOR_CI)) {
                if (!quarkusBotConfig.isDryRun()) {
                    pullRequest.removeLabels(Labels.TRIAGE_WAITING_FOR_CI);
                } else {
                    LOG.info("Pull request #" + pullRequest.getNumber() + " - Remove label: "
                            + Labels.TRIAGE_WAITING_FOR_CI);
                }
                break;
            }
        }
    }
}
