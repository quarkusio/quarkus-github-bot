package io.quarkus.bot;

import static io.quarkus.bot.util.Strings.SIMILAR_GITHUB_HANDLE_COMMENT_MARKER;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile.SimilarGitHubHandleCheck;
import io.quarkus.bot.service.TeamMemberService;
import io.quarkus.bot.util.GitHubHandleSimilarity;
import io.quarkus.bot.util.Strings;

class CheckPullRequestSimilarGitHubHandle {

    private static final Logger LOG = Logger.getLogger(CheckPullRequestSimilarGitHubHandle.class);

    private static final String WARNING_COMMENT = """
            > [!WARNING]
            > We noticed that your GitHub handle is very similar to that of a team member.
            >
            > To avoid any confusion, we wanted to flag this so that reviewers can double-check.
            > No action is needed on your part. Thank you for your understanding!""";

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    @Inject
    TeamMemberService teamMemberService;

    void checkPullRequestSimilarGitHubHandle(
            @PullRequest.Opened GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.CHECK_SIMILAR_GITHUB_HANDLES.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        SimilarGitHubHandleCheck config = quarkusBotConfigFile.similarGitHubHandleCheck;
        if (config.org == null || config.teams.isEmpty()) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        String login = pullRequest.getUser().getLogin();

        Set<String> teamMembers = teamMemberService.getTeamMembers(
                pullRequest.getRepository().getRoot(),
                config.org,
                config.teams);

        if (teamMembers.isEmpty()) {
            return;
        }

        Optional<String> similarMember = GitHubHandleSimilarity.findSimilarTeamMember(login, teamMembers);
        if (similarMember.isEmpty()) {
            return;
        }

        LOG.info("Pull Request #" + pullRequest.getNumber()
                + " - GitHub handle " + login + " is similar to a team member GitHub handle");

        String comment = Strings.commentByBot(WARNING_COMMENT + "\n\n/cc @" + similarMember.get())
                + SIMILAR_GITHUB_HANDLE_COMMENT_MARKER;

        if (!quarkusBotConfig.isDryRun()) {
            pullRequest.comment(comment);
        } else {
            LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add comment: " + comment);
        }
    }
}
