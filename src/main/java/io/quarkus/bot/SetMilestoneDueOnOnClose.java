package io.quarkus.bot;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.GitHubEvent;
import io.quarkiverse.githubapp.event.RawEvent;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.vertx.core.json.JsonObject;

class SetMilestoneDueOnOnClose {

    private static final Logger LOG = Logger.getLogger(SetMilestoneDueOnOnClose.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void setDueOnOnClose(
            @RawEvent(event = "milestone", action = "closed") GitHubEvent gitHubEvent,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            GitHub gitHub) throws IOException {
        if (!Feature.SET_MILESTONE_DUE_ON_ON_CLOSE.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        JsonObject payload = gitHubEvent.getParsedPayload();
        JsonObject milestonePayload = payload.getJsonObject("milestone");

        int milestoneNumber = milestonePayload.getInteger("number");
        String closedAt = milestonePayload.getString("closed_at");

        if (closedAt == null) {
            LOG.warn("Milestone #" + milestoneNumber + " - closed_at is null, cannot set due_on");
            return;
        }

        Date closedAtDate = Date.from(Instant.parse(closedAt));

        String repositoryFullName = payload.getJsonObject("repository").getString("full_name");
        GHRepository repository = gitHub.getRepository(repositoryFullName);
        GHMilestone milestone = repository.getMilestone(milestoneNumber);

        if (!quarkusBotConfig.isDryRun()) {
            milestone.setDueOn(closedAtDate);
        } else {
            LOG.info("Milestone #" + milestoneNumber + " - Set due_on: " + closedAt);
        }
    }
}
