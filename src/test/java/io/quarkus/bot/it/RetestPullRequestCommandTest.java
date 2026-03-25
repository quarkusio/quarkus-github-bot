package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
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
    void featureDisabledShouldCommentClearError() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, false);
            RetestGitHubMockSupport.allowWritePermission(mocks, true);
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT, true)
                .then().github(mocks -> {
                    verify(mocks.issue(2001)).comment(contains("Pull request workflow retest is disabled"));
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).isEmpty();
                });
    }

    @Test
    void nonCommitterShouldNotTriggerRerun() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true);
            RetestGitHubMockSupport.allowWritePermission(mocks, false);
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    verify(mocks.repository(RetestGitHubMockSupport.REPOSITORY)).hasPermission(any(GHUser.class),
                            eq(GHPermissionType.WRITE));
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).isEmpty();
                });
    }

    @Test
    void plainIssueCommentShouldNotTriggerCommand() throws Exception {
        given().github(mocks -> RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true))
                .when().payloadFromClasspath("/issue-comment-created-issue.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    verify(mocks.repository(RetestGitHubMockSupport.REPOSITORY), never()).getPullRequest(2);
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).isEmpty();
                });
    }

    @Test
    void malformedIssueCommentShouldNotTriggerParseErrorComment() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true);
            RetestGitHubMockSupport.allowWritePermission(mocks, true);
            when(mocks.issueComment(1002).getBody()).thenReturn("@quarkusbot \"retest");
        })
                .when().payloadFromClasspath("/issue-comment-created-issue.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    verify(mocks.issue(2002), never()).comment(org.mockito.ArgumentMatchers.anyString());
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).isEmpty();
                });
    }

    @Test
    void editedCommentShouldNotRetrigger() throws Exception {
        given().github(mocks -> RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true))
                .when().payloadFromClasspath("/issue-comment-edited-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    verify(mocks.repository(RetestGitHubMockSupport.REPOSITORY), never()).getPullRequest(1);
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).isEmpty();
                });
    }

    @ParameterizedTest(name = "parse error reply when featureEnabled={0}, writePermission={1}")
    @CsvSource({
            "true, true, true",
            "true, false, false",
            "false, true, false",
            "false, false, false"
    })
    void malformedCommandShouldRespectParseErrorGates(boolean featureEnabled, boolean hasWritePermission,
            boolean shouldComment) throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, featureEnabled);
            RetestGitHubMockSupport.allowWritePermission(mocks, hasWritePermission);
            RetestGitHubMockSupport.givenMalformedCommand(mocks);
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    if (shouldComment) {
                        verify(mocks.issue(2001)).comment(contains("Unable to parse the command"));
                    } else {
                        verify(mocks.issue(2001), never()).comment(org.mockito.ArgumentMatchers.anyString());
                    }
                });
    }

    @Test
    void aliasShouldTriggerCommand() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true);
            RetestGitHubMockSupport.allowWritePermission(mocks, true);
            GHRepository repository = RetestGitHubMockSupport.repository(mocks);
            RetestGitHubMockSupport.givenAliasCommand(mocks);
            RetestGitHubMockSupport.givenOpenPullRequestWithRuns(mocks,
                    RetestFixtures.failedCompletedRun(650L, 65L, "CI", 65L, 1L, repository));
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).containsExactly(650L));
    }

    @Test
    void multipleEligibleFailedWorkflowsShouldBeRerun() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true);
            RetestGitHubMockSupport.allowWritePermission(mocks, true);
            GHRepository repository = RetestGitHubMockSupport.repository(mocks);
            RetestGitHubMockSupport.givenOpenPullRequestWithRuns(mocks,
                    RetestFixtures.failedCompletedRun(801L, 80L, "CI", 80L, 1L, repository),
                    RetestFixtures.timedOutCompletedRun(802L, 81L, "Native", 81L, 1L, repository));
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).containsExactly(802L,
                        801L));
    }

    @Test
    void noEligibleRunsShouldCommentClearError() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true);
            RetestGitHubMockSupport.allowWritePermission(mocks, true);
            RetestGitHubMockSupport.givenOpenPullRequestWithRuns(mocks);
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT, true)
                .then().github(mocks -> {
                    verify(mocks.issue(2001)).comment(contains("No workflow runs matched the latest head"));
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).isEmpty();
                });
    }

    @Test
    void closedPullRequestShouldCommentClearError() throws Exception {
        given().github(mocks -> {
            RetestGitHubMockSupport.setRetestFeatureEnabled(mocks, true);
            RetestGitHubMockSupport.allowWritePermission(mocks, true);
            GHPullRequest pullRequest = RetestGitHubMockSupport.givenOpenPullRequestWithRuns(mocks);
            when(pullRequest.getState()).thenReturn(GHIssueState.CLOSED);
        })
                .when().payloadFromClasspath("/issue-comment-created-pull-request.json")
                .event(GHEvent.ISSUE_COMMENT, true)
                .then().github(mocks -> {
                    verify(mocks.issue(2001)).comment(contains("Retest is only available on open pull requests"));
                    assertThat(capturingFailedJobsRerunner.rerunWorkflowRunIds()).isEmpty();
                });
    }
}
