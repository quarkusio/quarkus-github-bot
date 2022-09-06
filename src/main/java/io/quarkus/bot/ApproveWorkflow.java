package io.quarkus.bot;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.util.PullRequestFilesMatcher;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositoryStatistics;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.PagedIterable;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class ApproveWorkflow {

    private static final Logger LOG = Logger.getLogger(ApproveWorkflow.class);

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void evaluatePullRequest(
            @WorkflowRun GHEventPayload.WorkflowRun workflowPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {
        if (!Feature.APPROVE_WORKFLOWS.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        // Don't bother checking if there are no rules
        if (quarkusBotConfigFile.workflows.rules != null && quarkusBotConfigFile.workflows.rules.isEmpty()) {
            return;
        }
        GHWorkflowRun workflowRun = workflowPayload.getWorkflowRun();

        // Only check workflows which need action
        if (!GHWorkflowRun.Conclusion.ACTION_REQUIRED.equals(workflowRun.getConclusion())) {
            return;
        }

        ApprovalStatus approval = new ApprovalStatus();

        checkUser(workflowPayload, quarkusBotConfigFile, approval);

        // Don't bother checking more if we have a red flag
        // (but don't return because we need to do stuff with the answer)
        if (!approval.hasRedFlag()) {
            checkFiles(quarkusBotConfigFile, workflowRun, approval);
        }

        if (approval.isApproved()) {
            processApproval(workflowRun);
        }
    }

    private void processApproval(GHWorkflowRun workflowRun) throws IOException {
        // We could also do things here like adding comments, subject to config
        if (!quarkusBotConfig.isDryRun()) {
            workflowRun.approve();
        }
    }

    private void checkUser(GHEventPayload.WorkflowRun workflowPayload, QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            ApprovalStatus approval) {
        for (QuarkusGitHubBotConfigFile.WorkflowApprovalRule rule : quarkusBotConfigFile.workflows.rules) {
            // We allow if the files or directories match the allow rule ...
            if ((rule.allow != null && rule.allow.users != null) || (rule.unless != null && rule.unless.users != null)) {
                GHRepositoryStatistics.ContributorStats stats = getStatsForUser(workflowPayload);
                if (matchRuleForUser(stats, rule.allow)) {
                    approval.shouldApprove = true;
                }

                if (matchRuleForUser(stats, rule.unless)) {
                    approval.shouldNotApprove = true;
                }
            }
        }
    }

    private void checkFiles(QuarkusGitHubBotConfigFile quarkusBotConfigFile, GHWorkflowRun workflowRun,
            ApprovalStatus approval) {
        String sha = workflowRun.getHeadSha();

        // Now we want to get the pull request we're supposed to be checking.
        // It would be nice to use commit.listPullRequests() but that only returns something if the
        // base and head of the PR are from the same repository, which rules out most scenarios where we would want to do an approval

        String fullyQualifiedBranchName = workflowRun.getHeadRepository().getOwnerName() + ":" + workflowRun.getHeadBranch();

        PagedIterable<GHPullRequest> pullRequestsForThisBranch = workflowRun.getRepository().queryPullRequests()
                .head(fullyQualifiedBranchName)
                .list();

        // The number of PRs with matching branch name should be exactly one, but if the PR
        // has been closed it sometimes disappears from the list; also, if two branch names
        // start with the same string, both will turn up in the query.
        for (GHPullRequest pullRequest : pullRequestsForThisBranch) {

            // Only look at PRs whose commit sha matches
            if (sha.equals(pullRequest.getHead().getSha())) {

                for (QuarkusGitHubBotConfigFile.WorkflowApprovalRule rule : quarkusBotConfigFile.workflows.rules) {
                    // We allow if the files or directories match the allow rule ...
                    if (matchRuleFromChangedFiles(pullRequest, rule.allow)) {
                        approval.shouldApprove = true;
                    }
                    // ... unless we also match the unless rule
                    if (matchRuleFromChangedFiles(pullRequest, rule.unless)) {
                        approval.shouldNotApprove = true;
                    }
                }
            }
        }
    }

    public static boolean matchRuleFromChangedFiles(GHPullRequest pullRequest,
            QuarkusGitHubBotConfigFile.WorkflowApprovalCondition rule) {
        // for now, we only use the files but we could also use the other rules at some point
        if (rule == null) {
            return false;
        }

        if (rule.files == null || rule.files.isEmpty()) {
            return false;
        }

        PullRequestFilesMatcher prMatcher = new PullRequestFilesMatcher(pullRequest);
        return prMatcher.changedFilesMatch(rule.files);
    }

    private boolean matchRuleForUser(GHRepositoryStatistics.ContributorStats stats,
            QuarkusGitHubBotConfigFile.WorkflowApprovalCondition rule) {
        if (rule == null || stats == null) {
            return false;
        }

        if (rule.users == null) {
            return false;
        }

        if (rule.users.minContributions != null && stats.getTotal() >= rule.users.minContributions) {
            return true;
        }

        // We can add more rules here, for example how long the user has been contributing

        return false;
    }

    private GHRepositoryStatistics.ContributorStats getStatsForUser(GHEventPayload.WorkflowRun workflowPayload) {

        String login = workflowPayload.getSender().getLogin();
        if (login != null) {
            return getStatsForUser(workflowPayload.getRepository(), login);
        }
        return null;
    }

    @CacheResult(cacheName = "contributor-cache")
    GHRepositoryStatistics.ContributorStats getStatsForUser(GHRepository repository, @CacheKey String login) {
        try {
            Map<String, GHRepositoryStatistics.ContributorStats> contributorStats = getContributorStats(repository);
            return contributorStats.get(login);
        } catch (IOException | InterruptedException e) {
            LOG.error("Could not get repository contributor statistics", e);
        }

        return null;
    }

    @CacheResult(cacheName = "stats-cache")
    Map<String, GHRepositoryStatistics.ContributorStats> getContributorStats(GHRepository repository)
            throws IOException, InterruptedException {
        GHRepositoryStatistics statistics = repository.getStatistics();
        if (statistics != null) {
            PagedIterable<GHRepositoryStatistics.ContributorStats> contributors = statistics.getContributorStats();
            // Pull the iterable into a list object to force the traversal of the entire list,
            // since then we get a fully-warmed cache on our first request
            // Convert to a map for convenience of retrieval
            List<GHRepositoryStatistics.ContributorStats> statsList = contributors.toList();
            return statsList.stream()
                    .collect(
                            Collectors.toMap(contributorStats -> contributorStats.getAuthor().getLogin(), Function.identity()));
        }
        return null;
    }

    private static class ApprovalStatus {
        // There are two variables here because we check a number of indicators and a number of counter-indicators
        // (ie green flags and red flags)
        boolean shouldApprove = false;
        boolean shouldNotApprove = false;

        boolean isApproved() {
            return shouldApprove && !shouldNotApprove;
        }

        public boolean hasRedFlag() {
            return shouldNotApprove;
        }
    }
}
