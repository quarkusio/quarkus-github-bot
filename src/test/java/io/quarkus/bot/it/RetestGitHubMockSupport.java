package io.quarkus.bot.it;

import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;

import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRunQueryBuilder;

import io.quarkiverse.githubapp.testing.dsl.GitHubMockSetupContext;
import io.quarkus.bot.retest.RetestFixtures;

public final class RetestGitHubMockSupport {

    public static final String REPOSITORY = "yrodiere/quarkus-bot-java-playground";

    private RetestGitHubMockSupport() {
    }

    public static void setRetestFeatureEnabled(GitHubMockSetupContext mocks, boolean enabled) throws IOException {
        mocks.configFile("quarkus-github-bot.yml")
                .fromString(enabled ? "features: [ RETEST_PULL_REQUEST_WORKFLOWS ]\n" : "features: [ APPROVE_WORKFLOWS ]\n");
    }

    public static void allowWritePermission(GitHubMockSetupContext mocks, boolean allowed) throws IOException {
        when(repository(mocks).hasPermission(any(GHUser.class), eq(GHPermissionType.WRITE))).thenReturn(allowed);
    }

    public static void givenMalformedCommand(GitHubMockSetupContext mocks) throws IOException {
        when(mocks.issueComment(1001).getBody()).thenReturn("@quarkusbot \"retest");
    }

    public static void givenAliasCommand(GitHubMockSetupContext mocks) throws IOException {
        when(mocks.issueComment(1001).getBody()).thenReturn("@quarkus-bot retest");
    }

    public static GHRepository repository(GitHubMockSetupContext mocks) {
        return mocks.repository(REPOSITORY);
    }

    public static GHPullRequest givenOpenPullRequestWithRuns(GitHubMockSetupContext mocks, GHWorkflowRun... workflowRuns)
            throws IOException {
        return givenOpenPullRequestWithRuns(mocks, repository(mocks), workflowRuns);
    }

    public static GHPullRequest givenOpenPullRequestWithRuns(GitHubMockSetupContext mocks, GHRepository headRepository,
            GHWorkflowRun... workflowRuns) throws IOException {
        GHRepository repository = repository(mocks);
        GHWorkflowRunQueryBuilder pullRequestRunsQuery = mock(GHWorkflowRunQueryBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF));
        GHWorkflowRunQueryBuilder pullRequestTargetRunsQuery = mock(GHWorkflowRunQueryBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF));
        GHPullRequestQueryBuilder pullRequestsQuery = mock(GHPullRequestQueryBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF));
        GHPullRequest pullRequest = RetestFixtures.openPullRequest(repository, headRepository);

        when(repository.getFullName()).thenReturn(REPOSITORY);
        when(repository.getOwnerName()).thenReturn("yrodiere");
        when(repository.getName()).thenReturn("quarkus-bot-java-playground");
        when(repository.getPullRequest(1)).thenReturn(pullRequest);
        when(repository.queryWorkflowRuns()).thenReturn(pullRequestRunsQuery, pullRequestTargetRunsQuery);
        when(pullRequestRunsQuery.list()).thenReturn(MockHelper.mockPagedIterable(workflowRuns));
        when(pullRequestTargetRunsQuery.list()).thenReturn(MockHelper.mockPagedIterable());
        when(repository.queryPullRequests()).thenReturn(pullRequestsQuery);
        when(pullRequestsQuery.list()).thenReturn(MockHelper.mockPagedIterable(pullRequest));

        return pullRequest;
    }
}
