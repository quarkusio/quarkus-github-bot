package io.quarkus.bot;

import java.io.IOException;

import jakarta.inject.Inject;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.service.GitHubBotActions;
import io.quarkus.bot.util.GHPullRequests;

class AddBranchToPullRequestTitle {

    @Inject
    GitHubBotActions gitHubBotActions;

    void addBranchToPullRequestTitle(@PullRequest.Opened GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.ADD_BRANCH_TO_PULL_REQUEST_TITLE.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        String baseBranch = pullRequest.getBase().getRef();

        String originalTitle = pullRequest.getTitle();
        String normalizedTitle = GHPullRequests.normalizeTitle(originalTitle, baseBranch);

        if (!originalTitle.equals(normalizedTitle)) {
            gitHubBotActions.setPullRequestTitle(pullRequest, normalizedTitle);
        }
    }
}
