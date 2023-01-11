package io.quarkus.bot;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.GHIssues;
import io.quarkus.bot.util.IssueExtractor;
import io.quarkus.bot.util.Labels;
import io.quarkus.bot.util.Strings;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

class AffectMilestone {

    private static final Logger LOG = Logger.getLogger(AffectMilestone.class);

    private static final String MASTER_BRANCH = "master";
    private static final String MAIN_BRANCH = "main";
    private static final String MAIN_MILESTONE_SUFFIX = "- main";

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void affectMilestone(@PullRequest.Closed GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.QUARKUS_REPOSITORY_WORKFLOW.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        GHRepository repository = pullRequestPayload.getRepository();
        String targetBranch = pullRequest.getBase().getRef();

        if (!pullRequest.isMerged()) {
            return;
        }
        if (!MASTER_BRANCH.equals(targetBranch) && !MAIN_BRANCH.equals(targetBranch)) {
            return;
        }

        if (GHIssues.hasLabel(pullRequest, Labels.TRIAGE_INVALID)) {
            pullRequest.removeLabels(Labels.TRIAGE_INVALID);
        }

        GHMilestone mainMilestone = getMainMilestone(pullRequestPayload.getRepository());
        if (mainMilestone == null) {
            LOG.error("Unable to find the main milestone");
            return;
        }

        GHMilestone currentMilestone = pullRequest.getMilestone();
        if (currentMilestone == null && !GHIssues.hasLabel(pullRequest, Labels.AREA_INFRA)) {
            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.setMilestone(mainMilestone);
            } else {
                LOG.info("Pull request #" + pullRequest.getNumber() + " - Affect milestone: " + mainMilestone.getTitle());
            }
        }

        Set<Integer> resolvedIssueNumbers = extractCurrentRepositoryIssueNumbers(pullRequest, gitHubGraphQLClient);
        Set<Integer> alreadyAffectedIssues = new TreeSet<>();

        for (Integer resolvedIssueNumber : resolvedIssueNumbers) {
            GHIssue resolvedIssue = repository.getIssue(resolvedIssueNumber);
            if (resolvedIssue == null) {
                continue;
            }

            if (resolvedIssue.getMilestone() != null
                    && (resolvedIssue.getMilestone().getNumber() != mainMilestone.getNumber())) {
                alreadyAffectedIssues.add(resolvedIssueNumber);
            } else {
                if (!quarkusBotConfig.isDryRun()) {
                    resolvedIssue.setMilestone(mainMilestone);
                } else {
                    LOG.info("Issue #" + resolvedIssueNumber + " - Affect milestone: " + mainMilestone.getTitle());
                }
            }
        }

        // Add a comment if some of the items were already affected to a different milestone
        String comment = "";
        if (currentMilestone != null && (currentMilestone.getNumber() != mainMilestone.getNumber())) {
            comment += "* The pull request itself\n";
        }
        for (Integer alreadyAffectedIssue : alreadyAffectedIssues) {
            comment += "* Issue #" + alreadyAffectedIssue + "\n";
        }
        if (!comment.isEmpty()) {
            comment = "Milestone is already set for some of the items:\n\n" + comment;
            comment += "\nWe haven't automatically updated the milestones for these items.";

            comment = Strings.commentByBot(comment);

            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.comment(comment);
            } else {
                LOG.info("Pull request #" + pullRequest.getNumber() + " - Add comment " + comment.toString());
            }
        }
    }

    private static GHMilestone getMainMilestone(GHRepository repository) {
        for (GHMilestone milestone : repository.listMilestones(GHIssueState.OPEN)) {
            if (milestone.getTitle().endsWith(MAIN_MILESTONE_SUFFIX)) {
                return milestone;
            }
        }
        return null;
    }

    private Set<Integer> extractCurrentRepositoryIssueNumbers(GHPullRequest pullRequest,
            DynamicGraphQLClient gitHubGraphQLClient) {
        String pullRequestBody = pullRequest.getBody();
        if (pullRequestBody == null || pullRequestBody.trim().isEmpty()) {
            return Collections.emptySet();
        }

        return new IssueExtractor(pullRequest.getRepository().getFullName()).extractIssueNumbers(pullRequest,
                gitHubGraphQLClient);
    }
}
