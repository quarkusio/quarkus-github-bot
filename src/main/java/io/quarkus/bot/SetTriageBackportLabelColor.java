package io.quarkus.bot;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Label;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.Labels;

public class SetTriageBackportLabelColor {

    private static final Logger LOG = Logger.getLogger(SetTriageBackportLabelColor.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    private static final String TRIAGE_BACKPORT_LABEL_COLOR = "7fe8cd";

    void setAreaLabelColor(@Label.Created GHEventPayload.Label labelPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.SET_TRIAGE_BACKPORT_LABEL_COLOR.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHLabel label = labelPayload.getLabel();

        if (!label.getName().startsWith(Labels.TRIAGE_BACKPORT_PREFIX)
                || TRIAGE_BACKPORT_LABEL_COLOR.equalsIgnoreCase(label.getColor())) {
            return;
        }

        if (!quarkusBotConfig.isDryRun()) {
            label.set().color(TRIAGE_BACKPORT_LABEL_COLOR);
        } else {
            LOG.info("Label " + label.getName() + " - Set color: #" + TRIAGE_BACKPORT_LABEL_COLOR);
        }
    }
}
