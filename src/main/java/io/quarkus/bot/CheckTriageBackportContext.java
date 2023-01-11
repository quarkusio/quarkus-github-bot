package io.quarkus.bot;

import java.io.IOException;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.Labels;
import io.quarkus.bot.util.Strings;

public class CheckTriageBackportContext {

    private static final Logger LOG = Logger.getLogger(CheckTriageBackportContext.class);

    public static final String LABEL_BACKPORT_WARNING = "triage/backport* labels may not be added to an issue. Please add them to the corresponding pull request.";

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void onLabel(@Issue.Labeled GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.CHECK_EDITORIAL_RULES.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHLabel label = issuePayload.getLabel();

        if (label.getName().startsWith(Labels.TRIAGE_BACKPORT_PREFIX)) {
            GHIssue issue = issuePayload.getIssue();
            String warningMsg = String.format(LABEL_BACKPORT_WARNING, label.getName());
            if (!quarkusBotConfig.isDryRun()) {
                issue.comment(Strings.commentByBot("@" + issuePayload.getSender().getLogin() + " " + warningMsg));
            } else {
                LOG.warn("Issue #" + issue.getNumber() + " - Add comment: " + warningMsg);
            }
        }
    }
}
