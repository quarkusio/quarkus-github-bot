package io.quarkus.bot;

import java.io.IOException;
import java.util.Locale;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;

import io.quarkiverse.githubapp.event.Label;
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.util.Labels;

public class SetAreaLabelColor {

    private static final Logger LOG = Logger.getLogger(SetAreaLabelColor.class);

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    private static final String AREA_LABEL_COLOR = "0366d6";

    void setAreaLabelColor(@Label.Created GHEventPayload.Label labelPayload) throws IOException {
        GHLabel label = labelPayload.getLabel();

        if (!label.getName().startsWith(Labels.AREA_PREFIX)
                || AREA_LABEL_COLOR.equals(label.getColor().toLowerCase(Locale.ROOT))) {
            return;
        }

        if (!quarkusBotConfig.isDryRun()) {
            label.set().color(AREA_LABEL_COLOR);
        } else {
            LOG.info("Label " + label.getName() + " - Set color: #" + AREA_LABEL_COLOR);
        }
    }
}
