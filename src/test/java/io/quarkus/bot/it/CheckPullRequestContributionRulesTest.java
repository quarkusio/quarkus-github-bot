package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHCheckRunBuilder.Output;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.bot.CheckPullRequestContributionRules;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class CheckPullRequestContributionRulesTest {

    private GHPullRequest mockPR;

    private GHRepository mockRepo;

    private GHCheckRunBuilder mockCheckRunBuilder;

    /**
     * Check of PR's commits is not processed and Check Run are not created in PR
     * if features's list in 'quarkus-github-bot.yml' file does not contain
     * CHECK_CONTRIBUTION_RULES
     */
    @Test
    void checkNotProcessedIfNotInConfig() throws IOException {

        GHCheckRunBuilder mockCheckRunBuilder = mock(GHCheckRunBuilder.class);

        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml").fromString("features: [ ]\n");
            mockPR = mocks.pullRequest(samplePullRequestId);
        })
                .when().payloadFromString(getSamplePullRequestPayload())
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {

                    verify(mockPR, times(0)).listCommits();

                    verify(mockCheckRunBuilder, times(0)).create();

                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    /**
     * If PR does not contain any merge commit nor fixup commit,
     * then 2 checks run in SUCCESS are created in PR
     */
    @Test
    void pullRequestHasTwoCheckRuns() throws IOException {

        setupMock();

        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml").fromString("features: [ CHECK_CONTRIBUTION_RULES ]\n");

            mockPR = mocks.pullRequest(samplePullRequestId);
            when(mockPR.getRepository()).thenReturn(mockRepo);

            setupMockHeadCommit();

            PagedIterable<GHPullRequestCommitDetail> iterableMock = MockHelper.mockPagedIterable();
            when(mockPR.listCommits()).thenReturn(iterableMock);
        })
                .when().payloadFromString(getSamplePullRequestPayload())
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {

                    verify(mockPR, times(1)).listCommits();

                    verify(mockRepo, times(1)).createCheckRun(eq(CheckPullRequestContributionRules.MERGE_COMMIT_CHECK_RUN_NAME),
                            eq(sampleHeadCommitSha));
                    verify(mockRepo, times(1)).createCheckRun(eq(CheckPullRequestContributionRules.FIXUP_COMMIT_CHECK_RUN_NAME),
                            eq(sampleHeadCommitSha));

                    verify(mockCheckRunBuilder, times(2)).withStatus(eq(GHCheckRun.Status.COMPLETED));
                    verify(mockCheckRunBuilder, times(2)).withConclusion(eq(GHCheckRun.Conclusion.SUCCESS));
                    verify(mockCheckRunBuilder, times(2)).create();

                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    /**
     * If PR contain 1 merge commit and not any fixup commit,
     * then 2 checks run are created in PR: 1 in FAILURE and 1 in SUCESS
     */
    @Test
    void pullRequestHasOneCheckRunSucessAndOneCheckRunFailIfMergeCommit() throws IOException {

        setupMock();

        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml").fromString("features: [ CHECK_CONTRIBUTION_RULES ]\n");

            mockPR = mocks.pullRequest(samplePullRequestId);
            when(mockPR.getRepository()).thenReturn(mockRepo);

            setupMockHeadCommit();

            GHPullRequestCommitDetail mockMergeCommitDetail = setupMockMergeCommit();
            PagedIterable<GHPullRequestCommitDetail> iterableMock = MockHelper.mockPagedIterable(mockMergeCommitDetail);
            when(mockPR.listCommits()).thenReturn(iterableMock);
        })
                .when().payloadFromString(getSamplePullRequestPayload())
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {

                    verify(mockPR, times(1)).listCommits();

                    verify(mockRepo, times(1)).createCheckRun(eq(CheckPullRequestContributionRules.MERGE_COMMIT_CHECK_RUN_NAME),
                            eq(sampleHeadCommitSha));
                    verify(mockRepo, times(1)).createCheckRun(eq(CheckPullRequestContributionRules.FIXUP_COMMIT_CHECK_RUN_NAME),
                            eq(sampleHeadCommitSha));

                    verify(mockCheckRunBuilder, times(2)).withStatus(eq(GHCheckRun.Status.COMPLETED));
                    verify(mockCheckRunBuilder, times(1)).withConclusion(eq(GHCheckRun.Conclusion.SUCCESS));
                    verify(mockCheckRunBuilder, times(1)).withConclusion(eq(GHCheckRun.Conclusion.FAILURE));
                    verify(mockCheckRunBuilder, times(1)).add(any(Output.class));
                    verify(mockCheckRunBuilder, times(2)).create();

                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    /**
     * If PR contain 1 fixup commit and not any merge commit,
     * then 2 checks run are created in PR: 1 in FAILURE and 1 in SUCESS
     */
    @Test
    void pullRequestHasOneCheckRunSucessAndOneCheckRunFailIfFixupCommit() throws IOException {

        setupMock();

        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml").fromString("features: [ CHECK_CONTRIBUTION_RULES ]\n");

            mockPR = mocks.pullRequest(samplePullRequestId);
            when(mockPR.getRepository()).thenReturn(mockRepo);

            setupMockHeadCommit();

            GHPullRequestCommitDetail mockFixupCommitDetail = setupMockFixupCommit();
            PagedIterable<GHPullRequestCommitDetail> iterableMock = MockHelper.mockPagedIterable(mockFixupCommitDetail);
            when(mockPR.listCommits()).thenReturn(iterableMock);
        })
                .when().payloadFromString(getSamplePullRequestPayload())
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {

                    verify(mockPR, times(1)).listCommits();

                    verify(mockRepo, times(1)).createCheckRun(eq(CheckPullRequestContributionRules.MERGE_COMMIT_CHECK_RUN_NAME),
                            eq(sampleHeadCommitSha));
                    verify(mockRepo, times(1)).createCheckRun(eq(CheckPullRequestContributionRules.FIXUP_COMMIT_CHECK_RUN_NAME),
                            eq(sampleHeadCommitSha));

                    verify(mockCheckRunBuilder, times(2)).withStatus(eq(GHCheckRun.Status.COMPLETED));
                    verify(mockCheckRunBuilder, times(1)).withConclusion(eq(GHCheckRun.Conclusion.SUCCESS));
                    verify(mockCheckRunBuilder, times(1)).withConclusion(eq(GHCheckRun.Conclusion.FAILURE));
                    verify(mockCheckRunBuilder, times(1)).add(any(Output.class));
                    verify(mockCheckRunBuilder, times(2)).create();

                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    private static long samplePullRequestId = 1091703530;

    private static String sampleHeadCommitSha = "7277231f08d6641edbdc07ee327dca1cc11e754d";

    private void setupMock() {
        mockCheckRunBuilder = mock(GHCheckRunBuilder.class);
        when(mockCheckRunBuilder.withStatus(any())).thenReturn(mockCheckRunBuilder);
        when(mockCheckRunBuilder.withStartedAt(any())).thenReturn(mockCheckRunBuilder);
        when(mockCheckRunBuilder.withConclusion(any())).thenReturn(mockCheckRunBuilder);

        mockRepo = mock(GHRepository.class);
        when(mockRepo.createCheckRun(any(), any())).thenReturn(mockCheckRunBuilder);
    }

    private void setupMockHeadCommit() throws IOException {
        GHCommitPointer mockHead = mock(GHCommitPointer.class);
        GHCommit mockHeadCommit = mock(GHCommit.class);
        when(mockPR.getHead()).thenReturn(mockHead);
        when(mockHead.getCommit()).thenReturn(mockHeadCommit);
        when(mockHeadCommit.getSHA1()).thenReturn(sampleHeadCommitSha);
    }

    private GHPullRequestCommitDetail setupMockMergeCommit() {
        GHPullRequestCommitDetail.Commit mockMergeCommit = mock(GHPullRequestCommitDetail.Commit.class);
        when(mockMergeCommit.getMessage()).thenReturn("This is a merge commit");
        GHPullRequestCommitDetail mockMergeCommitDetail = mock(GHPullRequestCommitDetail.class);
        GHPullRequestCommitDetail.CommitPointer mockCommitPointer = mock(GHPullRequestCommitDetail.CommitPointer.class);
        when(mockMergeCommitDetail.getParents())
                .thenReturn(new GHPullRequestCommitDetail.CommitPointer[] { mockCommitPointer, mockCommitPointer });
        when(mockMergeCommitDetail.getCommit()).thenReturn(mockMergeCommit);

        return mockMergeCommitDetail;
    }

    private GHPullRequestCommitDetail setupMockFixupCommit() {
        GHPullRequestCommitDetail.Commit mockFixupCommit = mock(GHPullRequestCommitDetail.Commit.class);
        String fixupCommitMessage = String.format("%s Sample Commit", CheckPullRequestContributionRules.FIXUP_COMMIT_PREFIX);
        when(mockFixupCommit.getMessage()).thenReturn(fixupCommitMessage);
        GHPullRequestCommitDetail mockFixupCommitDetail = mock(GHPullRequestCommitDetail.class);
        GHPullRequestCommitDetail.CommitPointer mockCommitPointer = mock(GHPullRequestCommitDetail.CommitPointer.class);
        when(mockFixupCommitDetail.getParents())
                .thenReturn(new GHPullRequestCommitDetail.CommitPointer[] { mockCommitPointer });
        when(mockFixupCommitDetail.getCommit()).thenReturn(mockFixupCommit);

        return mockFixupCommitDetail;
    }

    private static String getSamplePullRequestPayload() {
        return """
                {
                    "action": "opened",
                    "number": 26,
                    "pull_request": {
                      "url": "https://api.github.com/repos/TestUser/test-playground-repo/pulls/26",
                      "id": 1091703530,
                      "number": 26,
                      "state": "open",
                      "title": "Test Pull Request Title",
                      "user": {
                        "login": "TestUser",
                        "type": "User"
                      },
                      "body": null,
                      "created_at": "2022-10-19T04:06:36Z",
                      "updated_at": "2022-10-19T04:06:36Z",
                      "assignee": null,
                      "base": {
                        "label": "TestUser:main",
                        "ref": "main",
                        "sha": "585d5655021b58b521d76f2fb40b77d76fb4eea5"
                      }
                    },
                    "repository": {
                      "id": 528667691,
                      "name": "test-playground-repo",
                      "full_name": "TestUser/test-playground-repo"
                    },
                    "sender": {
                      "login": "TestUser",
                      "id": 8484741
                    }
                  }
                    """;
    }

}