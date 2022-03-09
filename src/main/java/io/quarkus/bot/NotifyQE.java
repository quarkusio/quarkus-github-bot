package io.quarkus.bot;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.Labels;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import javax.inject.Inject;
import java.io.IOException;

public class NotifyQE {

    private static final Logger LOG = Logger.getLogger(NotifyQE.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void commentOnIssue(@Issue.Labeled GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {

        comment(quarkusBotConfigFile, issuePayload.getIssue(), issuePayload.getLabel());
    }

    void commentOnPullRequest(@PullRequest.Labeled GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {

        comment(quarkusBotConfigFile, pullRequestPayload.getPullRequest(), pullRequestPayload.getLabel());
    }

    private void comment(QuarkusGitHubBotConfigFile quarkusBotConfigFile, GHIssue issue, GHLabel label) throws IOException {
        if (quarkusBotConfigFile == null) {
            LOG.error("Unable to find triage configuration.");
            return;
        }

        if (label.getName().equals(Labels.TRIAGE_QE)) {
            if (!quarkusBotConfig.isDryRun()) {
                if (!quarkusBotConfigFile.triage.qe.notify.isEmpty()) {
                    issue.comment("/cc @" + String.join(", @", quarkusBotConfigFile.triage.qe.notify));
                } else {
                    LOG.warn("Added label: " + Labels.TRIAGE_QE + ", but no QE config is available");
                }
            } else {
                LOG.info((issue instanceof GHPullRequest ? "Pull Request #" : "Issue #") + issue.getNumber() +
                        " - Added label: " + Labels.TRIAGE_QE +
                        " - Mentioning QE: " + quarkusBotConfigFile.triage.qe.notify);
            }
        }
    }
}
