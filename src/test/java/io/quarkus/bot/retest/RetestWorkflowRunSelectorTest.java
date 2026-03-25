package io.quarkus.bot.retest;

import static io.quarkus.bot.it.MockHelper.mockPagedIterable;
import static io.quarkus.bot.retest.RetestFixtures.DEFAULT_HEAD_SHA;
import static io.quarkus.bot.retest.RetestFixtures.failedCompletedRun;
import static io.quarkus.bot.retest.RetestFixtures.failedCompletedRunOnHead;
import static io.quarkus.bot.retest.RetestFixtures.failedCompletedRunWithJobs;
import static io.quarkus.bot.retest.RetestFixtures.openPullRequest;
import static io.quarkus.bot.retest.RetestFixtures.pullRequestFixture;
import static io.quarkus.bot.retest.RetestFixtures.queuedRun;
import static io.quarkus.bot.retest.RetestFixtures.repository;
import static io.quarkus.bot.retest.RetestFixtures.successfulCompletedRun;
import static io.quarkus.bot.retest.RetestFixtures.successfulJob;
import static io.quarkus.bot.retest.RetestFixtures.timedOutCompletedRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRunQueryBuilder;

class RetestWorkflowRunSelectorTest {

    private static final String REPOSITORY = "quarkusio/quarkus-github-bot";

    private final RetestWorkflowRunSelector selector = new RetestWorkflowRunSelector();

    @Test
    void shouldQueryWorkflowRunsAndSelectLatestFailedAttemptPerWorkflow() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun olderAttempt = failedCompletedRun(100L, 10L, "CI", 7L, 1L, scenario.repository);
        GHWorkflowRun latestAttempt = failedCompletedRun(101L, 10L, "CI", 7L, 2L, scenario.repository);
        GHWorkflowRun anotherWorkflow = timedOutCompletedRun(102L, 11L, "Native", 3L, 1L, scenario.repository);

        scenario.givenWorkflowRunsForPullRequest(olderAttempt, latestAttempt, anotherWorkflow);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(anotherWorkflow, latestAttempt);
        assertThat(workflowSelection.noEligibleReason()).isNull();
        verify(scenario.pullRequestRunsQuery).event("pull_request");
        verify(scenario.pullRequestRunsQuery).headSha(DEFAULT_HEAD_SHA);
    }

    @Test
    void shouldMatchForkHeadRepository() throws Exception {
        GHRepository repository = repository(REPOSITORY);
        GHRepository forkRepository = repository("someone-else/quarkus-github-bot");
        Scenario scenario = scenario(openPullRequest(repository, forkRepository));

        scenario.givenWorkflowRunsForPullRequest(failedCompletedRun(150L, 15L, "CI", 4L, 1L, repository, forkRepository));

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).hasSize(1);
    }

    @Test
    void shouldKeepSeparateRunsForSameWorkflowAcrossPullRequestAndPullRequestTargetEvents() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun pullRequestRun = failedCompletedRun(110L, 10L, "CI", 7L, 1L, scenario.repository);
        GHWorkflowRun pullRequestTargetRun = failedCompletedRun(111L, 10L, "CI", 8L, 1L, scenario.repository);

        scenario.givenWorkflowRunsForPullRequest(pullRequestRun)
                .givenWorkflowRunsForPullRequestTarget(pullRequestTargetRun);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(pullRequestTargetRun, pullRequestRun);
        assertThat(workflowSelection.noEligibleReason()).isNull();
    }

    @Test
    void shouldSelectRunAssociatedWithCurrentPullRequest() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun workflowRun = failedCompletedRun(177L, 18L, "CI", 6L, 1L, scenario.repository,
                scenario.currentPullRequest);

        scenario.givenWorkflowRunsForPullRequest(workflowRun);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(workflowRun);
        assertThat(workflowSelection.noEligibleReason()).isNull();
    }

    @Test
    void shouldIgnoreRunsAssociatedWithDifferentPullRequest() throws Exception {
        Scenario scenario = scenario();
        GHPullRequest otherPullRequest = pullRequestFixture(scenario.repository)
                .number(2)
                .build();
        GHWorkflowRun workflowRun = failedCompletedRun(175L, 17L, "CI", 5L, 1L, scenario.repository, otherPullRequest);

        scenario.givenWorkflowRunsForPullRequest(workflowRun);

        assertNoWorkflowRunsForHead(scenario.select());
    }

    @Test
    void shouldIgnoreOlderUnassociatedRunWhenCurrentPullRequestHasANewerAssociatedRun() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun associatedRun = successfulCompletedRun(178L, 18L, "CI", 7L, 1L, scenario.repository,
                scenario.currentPullRequest);
        GHWorkflowRun unassociatedRun = failedCompletedRun(179L, 18L, "CI", 6L, 1L, scenario.repository);

        scenario.givenWorkflowRunsForPullRequest(associatedRun, unassociatedRun);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).isEmpty();
        assertThat(workflowSelection.noEligibleReason())
                .isEqualTo(RetestWorkflowSelection.NoEligibleReason.LATEST_RUNS_GREEN);
    }

    @Test
    void shouldPreferNewerUnassociatedRunWhenCurrentPullRequestHasAnOlderAssociatedRun() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun associatedRun = successfulCompletedRun(180L, 18L, "CI", 6L, 1L, scenario.repository,
                scenario.currentPullRequest);
        GHWorkflowRun unassociatedRun = failedCompletedRun(181L, 18L, "CI", 7L, 1L, scenario.repository);

        scenario.givenWorkflowRunsForPullRequest(associatedRun, unassociatedRun);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(unassociatedRun);
        assertThat(workflowSelection.noEligibleReason()).isNull();
    }

    @Test
    void shouldKeepUnassociatedRunForDifferentWorkflowWhenCurrentPullRequestHasAssociatedRun() throws Exception {
        Scenario scenario = scenario();
        GHWorkflowRun associatedRun = successfulCompletedRun(182L, 18L, "CI", 7L, 1L, scenario.repository,
                scenario.currentPullRequest);
        GHWorkflowRun unassociatedRun = failedCompletedRun(183L, 19L, "Native", 6L, 1L, scenario.repository);

        scenario.givenWorkflowRunsForPullRequest(associatedRun, unassociatedRun);

        RetestWorkflowSelection workflowSelection = scenario.select();

        assertThat(workflowSelection.eligibleRuns()).containsExactly(unassociatedRun);
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("mismatchedWorkflowRuns")
    void shouldIgnoreRunsThatDoNotMatchCurrentPullRequestHead(String description, GHWorkflowRun workflowRun)
            throws Exception {
        Scenario scenario = scenario();

        scenario.givenWorkflowRunsForPullRequest(workflowRun);

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

    private static Stream<Arguments> mismatchedWorkflowRuns() {
        GHRepository repository = repository(REPOSITORY);

        return Stream.of(
                Arguments.of("different head repository", failedCompletedRunOnHead(600L, 60L, "CI", 12L, 1L,
                        DEFAULT_HEAD_SHA, "feature/retest", repository("someone-else/quarkus-github-bot"))),
                Arguments.of("different head sha", failedCompletedRunOnHead(630L, 63L, "CI", 18L, 1L, "cafebabe",
                        "feature/retest", repository)),
                Arguments.of("different head branch", failedCompletedRunOnHead(640L, 64L, "CI", 19L, 1L,
                        DEFAULT_HEAD_SHA, "other-branch", repository)));
    }

    private final class Scenario {

        private final GHRepository repository;
        private final GHPullRequest currentPullRequest;
        private final GHWorkflowRunQueryBuilder pullRequestRunsQuery;
        private final GHWorkflowRunQueryBuilder pullRequestTargetRunsQuery;

        private Scenario(GHPullRequest currentPullRequest) {
            this.repository = currentPullRequest.getRepository();
            this.currentPullRequest = currentPullRequest;
            this.pullRequestRunsQuery = mock(GHWorkflowRunQueryBuilder.class, withSettings().defaultAnswer(RETURNS_SELF));
            this.pullRequestTargetRunsQuery = mock(GHWorkflowRunQueryBuilder.class,
                    withSettings().defaultAnswer(RETURNS_SELF));

            when(repository.queryWorkflowRuns()).thenReturn(pullRequestRunsQuery, pullRequestTargetRunsQuery);
            when(pullRequestRunsQuery.list()).thenReturn(mockPagedIterable());
            when(pullRequestTargetRunsQuery.list()).thenReturn(mockPagedIterable());
        }

        private Scenario givenWorkflowRunsForPullRequest(GHWorkflowRun... workflowRuns) {
            when(pullRequestRunsQuery.list()).thenReturn(mockPagedIterable(workflowRuns));
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
