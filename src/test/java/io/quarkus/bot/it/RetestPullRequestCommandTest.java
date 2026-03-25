package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.bot.retest.CapturingFailedJobsRerunner;
import io.quarkus.bot.retest.RetestFixtures;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GitHubAppTest
public class RetestPullRequestCommandTest {

    @Inject
    CapturingFailedJobsRerunner capturingFailedJobsRerunner;

    @BeforeEach
    void resetRerunner() {
        capturingFailedJobsRerunner.reset();
    }

    @Test
    void nonCommitterShouldNotTriggerRerun() throws Exception {
        given().github(mocks -> {
            RetestFixtures.enableRetestFeature(mocks);
            RetestFixtures.allowWritePermission(mocks, false);
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    verify(mocks.repository(RetestFixtures.PLAYGROUND_REPOSITORY)).hasPermission(any(GHUser.class),
                            eq(GHPermissionType.WRITE));
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).isEmpty();
                });
    }

    @Test
    void multipleEligibleFailedWorkflowsShouldBeRerun() throws Exception {
        given().github(mocks -> {
            RetestFixtures.enableRetestFeature(mocks);
            RetestFixtures.allowWritePermission(mocks, true);
            GHRepository repository = mocks.repository(RetestFixtures.PLAYGROUND_REPOSITORY);
            RetestFixtures.givenOpenPullRequestWithRuns(mocks,
                    RetestFixtures.failedCompletedRun(801L, 80L, "CI", 80L, 1L, repository),
                    RetestFixtures.timedOutCompletedRun(802L, 81L, "Native", 81L, 1L, repository));
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).containsExactly(802L, 801L);
                    verify(mocks.issue(2001)).comment(contains("[#802](https://github.com/"
                            + RetestFixtures.PLAYGROUND_REPOSITORY + "/actions/runs/802), [#801](https://github.com/"
                            + RetestFixtures.PLAYGROUND_REPOSITORY + "/actions/runs/801)"));
                });
    }
}
