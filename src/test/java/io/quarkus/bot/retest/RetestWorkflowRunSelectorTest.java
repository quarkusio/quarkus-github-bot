package io.quarkus.bot.retest;

import static io.quarkus.bot.it.MockHelper.mockPagedIterable;
import static io.quarkus.bot.retest.RetestFixtures.DEFAULT_HEAD_SHA;
import static io.quarkus.bot.retest.RetestFixtures.closedPullRequestWithSameHead;
import static io.quarkus.bot.retest.RetestFixtures.failedCompletedRun;
import static io.quarkus.bot.retest.RetestFixtures.failedCompletedRunOnHead;
import static io.quarkus.bot.retest.RetestFixtures.failedCompletedRunWithJobs;
import static io.quarkus.bot.retest.RetestFixtures.failedJob;
import static io.quarkus.bot.retest.RetestFixtures.openPullRequest;
import static io.quarkus.bot.retest.RetestFixtures.pullRequestFixture;
import static io.quarkus.bot.retest.RetestFixtures.queuedRun;
import static io.quarkus.bot.retest.RetestFixtures.repository;
import static io.quarkus.bot.retest.RetestFixtures.startupFailureJob;
import static io.quarkus.bot.retest.RetestFixtures.successfulCompletedRun;
import static io.quarkus.bot.retest.RetestFixtures.successfulJob;
import static io.quarkus.bot.retest.RetestFixtures.timedOutCompletedRun;
import static io.quarkus.bot.retest.RetestFixtures.workflowRunFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRunQueryBuilder;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

class RetestWorkflowRunSelectorTest {

    private static final String REPOSITORY = "quarkusio/quarkus-github-bot";

    private final RetestWorkflowRunSelector selector = new RetestWorkflowRunSelector();

    @Test
    void shouldQueryWorkflowRunsAndSelectLatestFailedAttemptPerWorkflow() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun olderAttempt = failedCompletedRun(100L, 10L, "CI", 7L, 1L, scenario.repository);
        GHWorkflowRun latestAttempt = failedCompletedRun(101L, 10L, "CI", 7L, 2L, scenario.repository);
        GHWorkflowRun anotherWorkflow = timedOutCompletedRun(102L, 11L, "Native", 3L, 1L, scenario.repository);

        AtomicInteger requestedPageSize = new AtomicInteger();
        scenario.givenWorkflowRunsForPullRequest(capturingPagedIterable(requestedPageSize, olderAttempt, latestAttempt,
                anotherWorkflow));

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(anotherWorkflow, latestAttempt);
        assertThat(workflowSelection.noEligibleReason()).isNull();
        verify(scenario.pullRequestRunsQuery).event("pull_request");
        verify(scenario.pullRequestRunsQuery).headSha(DEFAULT_HEAD_SHA);
        assertThat(requestedPageSize).hasValue(30);
    }

    @Test
    void shouldQueryAllPullRequestsForForkHead() throws Exception {
        GHRepository repository = repository(REPOSITORY);
        GHRepository forkRepository = repository("someone-else/quarkus-github-bot");
        Scenario scenario = scenario(openPullRequest(repository, forkRepository));

        scenario.givenWorkflowRunsForPullRequest(failedCompletedRun(150L, 15L, "CI", 4L, 1L, forkRepository));

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).hasSize(1);
        verify(scenario.pullRequestsQuery).state(GHIssueState.ALL);
        verify(scenario.pullRequestsQuery).head("someone-else:feature/retest");
    }

    @Test
    void shouldKeepSeparateRunsForSameWorkflowAcrossPullRequestAndPullRequestTargetEvents() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun pullRequestRun = failedCompletedRun(110L, 10L, "CI", 7L, 1L, scenario.repository);
        GHWorkflowRun pullRequestTargetRun = failedCompletedRun(111L, 10L, "CI", 8L, 1L, scenario.repository,
                GHEvent.UNKNOWN);

        scenario.givenWorkflowRunsForPullRequest(pullRequestRun)
                .givenWorkflowRunsForPullRequestTarget(pullRequestTargetRun);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(pullRequestTargetRun, pullRequestRun);
        assertThat(workflowSelection.noEligibleReason()).isNull();
    }

    @Test
    void shouldSelectPullRequestTargetRunsSurfacedAsUnknownEvent() throws Exception {
        Scenario scenario = scenario();

        scenario.givenWorkflowRunsForPullRequestTarget(
                failedCompletedRun(176L, 18L, "CI", 6L, 1L, scenario.repository, GHEvent.UNKNOWN));

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).hasSize(1);
        assertThat(workflowSelection.noEligibleReason()).isNull();
    }

    @Test
    void shouldRejectMultiplePullRequestsForSameHead() {
        Scenario scenario = scenario();
        GHPullRequest otherPullRequest = pullRequestFixture(scenario.repository)
                .number(2)
                .build();

        scenario.givenMatchingPullRequests(scenario.currentPullRequest, otherPullRequest);

        assertThatThrownBy(scenario::select)
                .isInstanceOf(RetestCommandException.class)
                .hasMessageContaining("Multiple pull requests share the same head branch and commit");
    }

    @Test
    void shouldRejectClosedPullRequestsSharingTheSameHead() {
        Scenario scenario = scenario();
        GHPullRequest closedPullRequest = closedPullRequestWithSameHead(scenario.repository, 2);

        scenario.givenMatchingPullRequests(scenario.currentPullRequest, closedPullRequest);

        assertThatThrownBy(scenario::select)
                .isInstanceOf(RetestCommandException.class)
                .hasMessageContaining("Multiple pull requests share the same head branch and commit");
    }

    @Test
    void shouldIgnorePrefixMatchedBranchWhenCheckingHeadAmbiguity() throws Exception {
        Scenario scenario = scenario();
        GHPullRequest prefixMatchedPullRequest = pullRequestFixture(scenario.repository)
                .number(2)
                .headRef("feature/retest-2")
                .build();
        GHWorkflowRun workflowRun = failedCompletedRun(652L, 65L, "CI", 21L, 1L, scenario.repository);

        scenario.givenMatchingPullRequests(scenario.currentPullRequest, prefixMatchedPullRequest)
                .givenWorkflowRunsForPullRequest(workflowRun);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(workflowRun);
        assertThat(workflowSelection.noEligibleReason()).isNull();
    }

    @Test
    void shouldIgnoreQueuedOrInProgressLatestAttempt() throws Exception {
        Scenario scenario = scenario();

        scenario.givenWorkflowRunsForPullRequest(
                failedCompletedRun(200L, 20L, "CI", 8L, 1L, scenario.repository),
                queuedRun(201L, 20L, "CI", 8L, 2L, scenario.repository));

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).isEmpty();
        assertThat(workflowSelection.noEligibleReason())
                .isEqualTo(RetestWorkflowSelection.NoEligibleReason.LATEST_RUNS_NOT_COMPLETED);
    }

    @Test
    void shouldIgnoreOlderFailedAttemptWhenLaterAttemptSucceeded() throws Exception {
        Scenario scenario = scenario();

        scenario.givenWorkflowRunsForPullRequest(
                failedCompletedRun(300L, 30L, "CI", 9L, 1L, scenario.repository),
                successfulCompletedRun(301L, 30L, "CI", 9L, 2L, scenario.repository));

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).isEmpty();
        assertThat(workflowSelection.noEligibleReason()).isEqualTo(RetestWorkflowSelection.NoEligibleReason.LATEST_RUNS_GREEN);
    }

    @Test
    void shouldRequireFailedLatestJobs() throws Exception {
        Scenario scenario = scenario();
        AtomicInteger listJobsCalls = new AtomicInteger();

        scenario.givenWorkflowRunsForPullRequest(
                failedCompletedRunWithJobs(400L, 40L, "CI", 10L, 1L, scenario.repository, listJobsCalls,
                        successfulJob()));

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).isEmpty();
        assertThat(workflowSelection.noEligibleReason())
                .isEqualTo(RetestWorkflowSelection.NoEligibleReason.NO_RERUNNABLE_FAILED_JOBS);
        assertThat(listJobsCalls).hasValue(1);
    }

    @Test
    void shouldUseNameAndRunNumberFallbackIdentity() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun olderAttempt = workflowRunFixture(500L, 0L, "CI")
                .runNumber(11L)
                .runAttempt(1L)
                .headRepository(scenario.repository)
                .completed(GHWorkflowRun.Conclusion.FAILURE)
                .jobs(failedJob())
                .build();
        GHWorkflowRun latestAttempt = workflowRunFixture(501L, 0L, "CI")
                .runNumber(11L)
                .runAttempt(2L)
                .headRepository(scenario.repository)
                .completed(GHWorkflowRun.Conclusion.FAILURE)
                .jobs(startupFailureJob())
                .build();

        scenario.givenWorkflowRunsForPullRequest(olderAttempt, latestAttempt);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(latestAttempt);
        assertThat(workflowSelection.noEligibleReason()).isNull();
    }

    @Test
    void shouldIgnoreRunsFromDifferentHeadRepository() throws Exception {
        Scenario scenario = scenario();
        GHRepository foreignHeadRepository = repository("someone-else/quarkus-github-bot");

        scenario.givenWorkflowRunsForPullRequest(
                failedCompletedRunOnHead(600L, 60L, "CI", 12L, 1L, DEFAULT_HEAD_SHA, "feature/retest",
                        foreignHeadRepository));

        assertNoWorkflowRunsForHead(scenario.select());
    }

    @Test
    void shouldSelectRunsWithoutPullRequestAssociationsWhenHeadIsUnique() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun workflowRun = failedCompletedRunOnHead(610L, 61L, "CI", 13L, 1L, DEFAULT_HEAD_SHA,
                "feature/retest", scenario.repository);

        scenario.givenWorkflowRunsForPullRequest(workflowRun);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(workflowRun);
        assertThat(workflowSelection.noEligibleReason()).isNull();
    }

    @Test
    void shouldPreferNewerWorkflowRunOverOlderRerunAttempt() throws Exception {
        Scenario scenario = scenario();

        scenario.givenWorkflowRunsForPullRequest(
                failedCompletedRun(620L, 64L, "CI", 16L, 2L, scenario.repository),
                failedCompletedRun(621L, 64L, "CI", 17L, 1L, scenario.repository));

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).extracting(GHWorkflowRun::getId).containsExactly(621L);
        assertThat(workflowSelection.noEligibleReason()).isNull();
    }

    @Test
    void shouldIgnoreRunsFromAnOlderHeadSha() throws Exception {
        Scenario scenario = scenario();

        scenario.givenWorkflowRunsForPullRequest(
                failedCompletedRunOnHead(630L, 63L, "CI", 18L, 1L, "cafebabe", "feature/retest", scenario.repository));

        assertNoWorkflowRunsForHead(scenario.select());
    }

    @Test
    void shouldIgnoreRunsFromADifferentHeadBranch() throws Exception {
        Scenario scenario = scenario();

        scenario.givenWorkflowRunsForPullRequest(
                failedCompletedRunOnHead(640L, 64L, "CI", 19L, 1L, DEFAULT_HEAD_SHA, "other-branch",
                        scenario.repository));

        assertNoWorkflowRunsForHead(scenario.select());
    }

    private Scenario scenario() {
        GHRepository repository = repository(REPOSITORY);
        return scenario(openPullRequest(repository));
    }

    private Scenario scenario(GHPullRequest currentPullRequest) {
        return new Scenario(currentPullRequest);
    }

    private static void assertNoWorkflowRunsForHead(RetestWorkflowSelection workflowSelection) {
        assertThat(workflowSelection.eligibleRuns()).isEmpty();
        assertThat(workflowSelection.noEligibleReason())
                .isEqualTo(RetestWorkflowSelection.NoEligibleReason.NO_WORKFLOW_RUNS_FOR_HEAD);
    }

    @SafeVarargs
    private static <T> PagedIterable<T> capturingPagedIterable(AtomicInteger requestedPageSize, T... contentMocks) {
        return new PagedIterable<>() {
            @Override
            public PagedIterator<T> _iterator(int pageSize) {
                requestedPageSize.set(pageSize);
                return mockPagedIterator(contentMocks);
            }

            @Override
            public List<T> toList() {
                return List.of(contentMocks);
            }
        };
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static <T> PagedIterator<T> mockPagedIterator(T... contentMocks) {
        PagedIterator<T> iteratorMock = mock(PagedIterator.class);
        Iterator<T> actualIterator = List.of(contentMocks).iterator();
        when(iteratorMock.next()).thenAnswer(ignored -> actualIterator.next());
        when(iteratorMock.hasNext()).thenAnswer(ignored -> actualIterator.hasNext());
        return iteratorMock;
    }

    private final class Scenario {

        private final GHRepository repository;
        private final GHPullRequest currentPullRequest;
        private final GHWorkflowRunQueryBuilder pullRequestRunsQuery;
        private final GHWorkflowRunQueryBuilder pullRequestTargetRunsQuery;
        private final GHPullRequestQueryBuilder pullRequestsQuery;

        private Scenario(GHPullRequest currentPullRequest) {
            this.repository = currentPullRequest.getRepository();
            this.currentPullRequest = currentPullRequest;
            this.pullRequestRunsQuery = mock(GHWorkflowRunQueryBuilder.class, withSettings().defaultAnswer(RETURNS_SELF));
            this.pullRequestTargetRunsQuery = mock(GHWorkflowRunQueryBuilder.class,
                    withSettings().defaultAnswer(RETURNS_SELF));
            this.pullRequestsQuery = mock(GHPullRequestQueryBuilder.class, withSettings().defaultAnswer(RETURNS_SELF));

            when(repository.queryWorkflowRuns()).thenReturn(pullRequestRunsQuery, pullRequestTargetRunsQuery);
            when(pullRequestRunsQuery.list()).thenReturn(mockPagedIterable());
            when(pullRequestTargetRunsQuery.list()).thenReturn(mockPagedIterable());
            when(repository.queryPullRequests()).thenReturn(pullRequestsQuery);
            when(pullRequestsQuery.list()).thenReturn(mockPagedIterable(currentPullRequest));
        }

        private Scenario givenMatchingPullRequests(GHPullRequest... pullRequests) {
            when(pullRequestsQuery.list()).thenReturn(mockPagedIterable(pullRequests));
            return this;
        }

        private Scenario givenWorkflowRunsForPullRequest(GHWorkflowRun... workflowRuns) {
            return givenWorkflowRunsForPullRequest(mockPagedIterable(workflowRuns));
        }

        private Scenario givenWorkflowRunsForPullRequest(PagedIterable<GHWorkflowRun> workflowRuns) {
            when(pullRequestRunsQuery.list()).thenReturn(workflowRuns);
            return this;
        }

        private Scenario givenWorkflowRunsForPullRequestTarget(GHWorkflowRun... workflowRuns) {
            when(pullRequestTargetRunsQuery.list()).thenReturn(mockPagedIterable(workflowRuns));
            return this;
        }

        private RetestWorkflowSelection select() throws IOException {
            return selector.selectWorkflowRuns(currentPullRequest);
        }
    }
}
