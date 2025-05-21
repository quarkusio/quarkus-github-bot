package io.quarkus.bot;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.GHIssues;
import io.quarkus.bot.util.GHPullRequests;
import io.quarkus.bot.util.Labels;

class RemoveInvalidLabelOnReopenAction {
    private static final Logger LOG = Logger.getLogger(RemoveInvalidLabelOnReopenAction.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    public void onIssueReopen(@Issue.Reopened GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.QUARKUS_REPOSITORY_WORKFLOW.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHIssue issue = issuePayload.getIssue();

        if (GHIssues.hasLabel(issue, Labels.TRIAGE_INVALID)) {
            if (!quarkusBotConfig.isDryRun()) {
                issue.removeLabel(Labels.TRIAGE_INVALID);
            } else {
                LOG.info("Issue #" + issue.getNumber() + " - Remove label: " + Labels.TRIAGE_INVALID);
            }
        }
    }

    public void onPullRequestReopen(@PullRequest.Reopened GHEventPayload.PullRequest pullRequestPayload) throws IOException {
        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();

        if (GHPullRequests.hasLabel(pullRequest, Labels.TRIAGE_INVALID)) {
            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.removeLabel(Labels.TRIAGE_INVALID);
            } else {
                LOG.info("Pull request #" + pullRequest.getNumber() + " - Remove label: " + Labels.TRIAGE_INVALID);
            }
        }
    }

}
