package io.quarkus.bot;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.runtime.StartupEvent;

public class QuarkusBot {

    private static final Logger LOG = Logger.getLogger(QuarkusBot.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void init(@Observes StartupEvent startupEvent) {
        if (quarkusBotConfig.isDryRun()) {
            LOG.warn("››› Quarkus Bot running in dry-run mode");
        }
    }
}
