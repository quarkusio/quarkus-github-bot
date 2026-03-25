package io.quarkus.bot.retest;

import static io.quarkus.bot.it.MockHelper.mockPagedIterable;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRunQueryBuilder;

import io.quarkiverse.githubapp.testing.dsl.GitHubMockSetupContext;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;

public final class RetestFixtures {

    public static final String PLAYGROUND_REPOSITORY = "yrodiere/quarkus-bot-java-playground";

    static final String DEFAULT_HEAD_SHA = "deadbeef";
    private static final String DEFAULT_HEAD_BRANCH = "feature/retest";

    private RetestFixtures() {
    }

    static GHRepository repository(String fullName) {
        GHRepository repository = mock(GHRepository.class);
        stubRepositoryIdentity(repository, fullName);
        return repository;
    }

    static PullRequestFixture pullRequestFixture(GHRepository repository) {
        return new PullRequestFixture(repository);
    }

    static GHPullRequest openPullRequest(GHRepository repository) {
        return pullRequestFixture(repository).build();
    }

    static GHPullRequest openPullRequest(GHRepository repository, GHRepository headRepository) {
        return pullRequestFixture(repository).headRepository(headRepository).build();
    }

    static QuarkusGitHubBotConfigFile configFileWithRetestFeatureEnabled() throws Exception {
        QuarkusGitHubBotConfigFile quarkusBotConfigFile = new QuarkusGitHubBotConfigFile();
        Field featuresField = QuarkusGitHubBotConfigFile.class.getDeclaredField("features");
        featuresField.setAccessible(true);
        featuresField.set(quarkusBotConfigFile, new HashSet<>(Set.of(Feature.RETEST_PULL_REQUEST_WORKFLOWS)));
        return quarkusBotConfigFile;
    }

    public static void enableRetestFeature(GitHubMockSetupContext mocks) throws IOException {
        mocks.configFile("quarkus-github-bot.yml").fromString("features: [ RETEST_PULL_REQUEST_WORKFLOWS ]\n");
    }

    public static void allowWritePermission(GitHubMockSetupContext mocks, boolean allowed) throws IOException {
        when(mocks.repository(PLAYGROUND_REPOSITORY).hasPermission(any(GHUser.class), eq(GHPermissionType.WRITE)))
                .thenReturn(allowed);
    }

    public static void givenOpenPullRequestWithRuns(GitHubMockSetupContext mocks, GHWorkflowRun... workflowRuns)
            throws IOException {
        GHRepository repository = mocks.repository(PLAYGROUND_REPOSITORY);
        GHWorkflowRunQueryBuilder pullRequestRunsQuery = mock(GHWorkflowRunQueryBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF));
        GHWorkflowRunQueryBuilder pullRequestTargetRunsQuery = mock(GHWorkflowRunQueryBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF));
        GHPullRequest pullRequest = openPullRequest(repository);

        stubRepositoryIdentity(repository, PLAYGROUND_REPOSITORY);
        when(repository.getPullRequest(1)).thenReturn(pullRequest);
        when(repository.queryWorkflowRuns()).thenReturn(pullRequestRunsQuery, pullRequestTargetRunsQuery);
        when(pullRequestRunsQuery.list()).thenReturn(mockPagedIterable(workflowRuns));
        when(pullRequestTargetRunsQuery.list()).thenReturn(mockPagedIterable());
    }

    public static GHWorkflowRun failedCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository repository) {
        return failedCompletedRun(id, workflowId, name, runNumber, runAttempt, repository, repository);
    }

    public static GHWorkflowRun failedCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository repository, GHRepository headRepository) {
        return failedCompletedRun(id, workflowId, name, runNumber, runAttempt, repository, headRepository, List.of());
    }

    public static GHWorkflowRun failedCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository repository, GHPullRequest associatedPullRequest) {
        return failedCompletedRun(id, workflowId, name, runNumber, runAttempt, repository, repository,
                List.of(associatedPullRequest));
    }

    private static GHWorkflowRun failedCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository repository, List<GHPullRequest> associatedPullRequests) {
        return failedCompletedRun(id, workflowId, name, runNumber, runAttempt, repository, repository,
                associatedPullRequests);
    }

    public static GHWorkflowRun failedCompletedRunOnHead(long id, long workflowId, String name, long runNumber,
            long runAttempt, String headSha, String headBranch, GHRepository repository) {
        return workflowRun(id, workflowId, name, runNumber, runAttempt, headSha, headBranch, repository, repository,
                GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.FAILURE, List.of(), null, failedJob());
    }

    public static GHWorkflowRun failedCompletedRunWithJobs(long id, long workflowId, String name, long runNumber,
            long runAttempt, GHRepository repository, AtomicInteger listJobsCalls, GHWorkflowJob... jobs) {
        return workflowRun(id, workflowId, name, runNumber, runAttempt, DEFAULT_HEAD_SHA, DEFAULT_HEAD_BRANCH, repository,
                repository, GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.FAILURE, List.of(), listJobsCalls, jobs);
    }

    public static GHWorkflowRun timedOutCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository repository) {
        return workflowRun(id, workflowId, name, runNumber, runAttempt, DEFAULT_HEAD_SHA, DEFAULT_HEAD_BRANCH, repository,
                repository, GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.TIMED_OUT, List.of(), null,
                timedOutJob());
    }

    public static GHWorkflowRun successfulCompletedRun(long id, long workflowId, String name, long runNumber,
            long runAttempt, GHRepository repository) {
        return successfulCompletedRun(id, workflowId, name, runNumber, runAttempt, repository, List.of());
    }

    public static GHWorkflowRun successfulCompletedRun(long id, long workflowId, String name, long runNumber,
            long runAttempt, GHRepository repository, GHPullRequest associatedPullRequest) {
        return successfulCompletedRun(id, workflowId, name, runNumber, runAttempt, repository,
                List.of(associatedPullRequest));
    }

    private static GHWorkflowRun successfulCompletedRun(long id, long workflowId, String name, long runNumber,
            long runAttempt, GHRepository repository, List<GHPullRequest> associatedPullRequests) {
        return workflowRun(id, workflowId, name, runNumber, runAttempt, DEFAULT_HEAD_SHA, DEFAULT_HEAD_BRANCH, repository,
                repository, GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.SUCCESS, associatedPullRequests, null,
                successfulJob());
    }

    public static GHWorkflowRun queuedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository repository) {
        return workflowRun(id, workflowId, name, runNumber, runAttempt, DEFAULT_HEAD_SHA, DEFAULT_HEAD_BRANCH, repository,
                repository, GHWorkflowRun.Status.QUEUED, null, List.of(), null, failedJob());
    }

    static GHWorkflowJob successfulJob() {
        return workflowJob(GHWorkflowRun.Conclusion.SUCCESS);
    }

    private static GHWorkflowRun failedCompletedRun(long id, long workflowId, String name, long runNumber,
            long runAttempt, GHRepository repository, GHRepository headRepository,
            List<GHPullRequest> associatedPullRequests) {
        return workflowRun(id, workflowId, name, runNumber, runAttempt, DEFAULT_HEAD_SHA, DEFAULT_HEAD_BRANCH, repository,
                headRepository, GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.FAILURE, associatedPullRequests,
                null, failedJob());
    }

    private static GHWorkflowJob failedJob() {
        return workflowJob(GHWorkflowRun.Conclusion.FAILURE);
    }

    private static GHWorkflowJob timedOutJob() {
        return workflowJob(GHWorkflowRun.Conclusion.TIMED_OUT);
    }

    private static GHWorkflowJob workflowJob(GHWorkflowRun.Conclusion conclusion) {
        GHWorkflowJob workflowJob = mock(GHWorkflowJob.class);
        when(workflowJob.getConclusion()).thenReturn(conclusion);
        return workflowJob;
    }

    private static GHWorkflowRun workflowRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            String headSha, String headBranch, GHRepository repository, GHRepository headRepository,
            GHWorkflowRun.Status status, GHWorkflowRun.Conclusion conclusion, List<GHPullRequest> associatedPullRequests,
            AtomicInteger listJobsCalls, GHWorkflowJob... jobs) {
        return new GHWorkflowRun() {
            @Override
            public long getId() {
                return id;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public long getRunNumber() {
                return runNumber;
            }

            @Override
            public long getWorkflowId() {
                return workflowId;
            }

            @Override
            public long getRunAttempt() {
                return runAttempt;
            }

            @Override
            public String getHeadSha() {
                return headSha;
            }

            @Override
            public String getHeadBranch() {
                return headBranch;
            }

            @Override
            public GHRepository getRepository() {
                return repository;
            }

            @Override
            public GHRepository getHeadRepository() {
                return headRepository;
            }

            @Override
            public GHWorkflowRun.Status getStatus() {
                return status;
            }

            @Override
            public GHWorkflowRun.Conclusion getConclusion() {
                return conclusion;
            }

            @Override
            public List<GHPullRequest> getPullRequests() {
                return associatedPullRequests;
            }

            @Override
            public URL getHtmlUrl() throws IOException {
                return new URL("https://github.com/" + repository.getFullName() + "/actions/runs/" + id);
            }

            @Override
            public org.kohsuke.github.PagedIterable<GHWorkflowJob> listJobs() {
                if (listJobsCalls != null) {
                    listJobsCalls.incrementAndGet();
                }
                return mockPagedIterable(jobs);
            }
        };
    }

    private static void stubRepositoryIdentity(GHRepository repository, String fullName) {
        String[] parts = fullName.split("/", 2);
        when(repository.getFullName()).thenReturn(fullName);
        when(repository.getOwnerName()).thenReturn(parts[0]);
        when(repository.getName()).thenReturn(parts[1]);
    }

    static final class PullRequestFixture {

        private final GHRepository repository;
        private int number = 1;
        private GHRepository headRepository;

        private PullRequestFixture(GHRepository repository) {
            this.repository = repository;
            this.headRepository = repository;
        }

        PullRequestFixture number(int number) {
            this.number = number;
            return this;
        }

        PullRequestFixture headRepository(GHRepository headRepository) {
            this.headRepository = headRepository;
            return this;
        }

        GHPullRequest build() {
            GHPullRequest pullRequest = mock(GHPullRequest.class);
            GHCommitPointer head = mock(GHCommitPointer.class);
            when(pullRequest.getNumber()).thenReturn(number);
            when(pullRequest.getState()).thenReturn(GHIssueState.OPEN);
            when(pullRequest.getRepository()).thenReturn(repository);
            when(pullRequest.getHead()).thenReturn(head);
            when(head.getRef()).thenReturn(DEFAULT_HEAD_BRANCH);
            when(head.getSha()).thenReturn(DEFAULT_HEAD_SHA);
            when(head.getRepository()).thenReturn(headRepository);
            return pullRequest;
        }
    }
}
