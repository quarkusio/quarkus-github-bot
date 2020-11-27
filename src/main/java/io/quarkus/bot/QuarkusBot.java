package io.quarkus.bot;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.runtime.StartupEvent;

public class QuarkusBot {

    private static final Logger LOG = Logger.getLogger(QuarkusBot.class);

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void init(@Observes StartupEvent startupEvent) {
        if (quarkusBotConfig.isDryRun()) {
            LOG.warn("››› Quarkus Bot running in dry-run mode");
        }
    }
}
