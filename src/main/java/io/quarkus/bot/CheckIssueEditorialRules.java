package io.quarkus.bot;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.Strings;

public class CheckIssueEditorialRules {
    private static final Logger LOG = Logger.getLogger(CheckIssueEditorialRules.class);

    private static final String ZULIP_URL = "https://quarkusio.zulipchat.com/";
    public static final String ZULIP_WARNING = Strings.commentByBot(
            "You added a link to a Zulip discussion, please make sure the description of the issue is comprehensive and doesn't require accessing Zulip");

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void onOpen(@Issue.Opened GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.CHECK_EDITORIAL_RULES.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHIssue issue = issuePayload.getIssue();
        String body = issue.getBody();

        if (body == null || !body.contains(ZULIP_URL)) {
            return;
        }

        if (!quarkusBotConfig.isDryRun()) {
            issue.comment(ZULIP_WARNING);
        } else {
            LOG.info("Issue #" + issue.getNumber() + " - Add comment " + ZULIP_WARNING);
        }
    }
}
