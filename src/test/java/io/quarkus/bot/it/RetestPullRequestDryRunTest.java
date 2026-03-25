package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHReaction;
import org.kohsuke.github.ReactionContent;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkiverse.githubapp.testing.dsl.GitHubMockVerificationContext;
import io.quarkus.bot.it.support.DryRunTestProfile;
import io.quarkus.bot.retest.CapturingFailedJobsRerunner;
import io.quarkus.bot.retest.RetestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@GitHubAppTest
@TestProfile(DryRunTestProfile.class)
public class RetestPullRequestDryRunTest {

    @Inject
    CapturingFailedJobsRerunner capturingFailedJobsRerunner;

    @BeforeEach
    void resetRerunner() {
        capturingFailedJobsRerunner.reset();
    }

    @Test
    void dryRunShouldNotTriggerRerun() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true);
            RetestGitHubMockSupport.allowWritePermission(mocks, true);
            RetestGitHubMockSupport.givenOpenPullRequestWithRuns(mocks,
                    RetestFixtures.failedCompletedRun(1501L, 150L, "CI", 150L, 1L,
                            RetestGitHubMockSupport.repository(mocks)));
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).isEmpty();
                    verifyNoReactions(mocks);
                });
    }

    @Test
    void dryRunShouldNotPostExecutionErrorComments() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, false);
            RetestGitHubMockSupport.allowWritePermission(mocks, true);
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT, true)
                .then().github(mocks -> {
                    verify(mocks.issue(2001), never()).comment(anyString());
                    verifyNoReactions(mocks);
                });
    }

    @Test
    void dryRunShouldNotPostParseErrorComments() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true);
            RetestGitHubMockSupport.allowWritePermission(mocks, true);
            RetestGitHubMockSupport.givenMalformedCommand(mocks);
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    verify(mocks.issue(2001), never()).comment(anyString());
                    verifyNoReactions(mocks);
                });
    }

    private static void verifyNoReactions(GitHubMockVerificationContext mocks) throws Exception {
        verify(mocks.issueComment(1001), never()).createReaction(any(ReactionContent.class));
        verify(mocks.issueComment(1001), never()).deleteReaction(any(GHReaction.class));
    }
}
