package io.quarkus.bot.it;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkiverse.githubapp.testing.dsl.GitHubMockSetupContext;
import io.quarkiverse.githubapp.testing.dsl.GitHubMockVerificationContext;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositoryStatistics;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.PagedIterable;
import org.mockito.Answers;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static io.quarkus.bot.it.MockHelper.mockUser;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class WorkflowApprovalTest {

    // We may change our user stats in individual tests, so wipe caches before each test
    @CacheInvalidateAll(cacheName = "contributor-cache")
    @CacheInvalidateAll(cacheName = "stats-cache")
    void setupMockQueriesAndCommits(GitHubMockSetupContext mocks) {
        GHRepository repoMock = mocks.repository("bot-playground");
        GHPullRequestQueryBuilder workflowRunQueryBuilderMock = mock(GHPullRequestQueryBuilder.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF));
        when(repoMock.queryPullRequests())
                .thenReturn(workflowRunQueryBuilderMock);
        PagedIterable<GHPullRequest> iterableMock = MockHelper.mockPagedIterable(pr(mocks));
        when(workflowRunQueryBuilderMock.list())
                .thenReturn(iterableMock);

        GHCommitPointer head = mock(GHCommitPointer.class);
        when(pr(mocks).getHead()).thenReturn(head);
        when(head.getSha()).thenReturn("f2b91b5e80e1880f03a91fdde381bb24debf102c");
    }

    private void setupMockUsers(GitHubMockSetupContext mocks) throws InterruptedException, IOException {
        GHRepository repoMock = mocks.repository("bot-playground");
        GHRepositoryStatistics stats = mock(GHRepositoryStatistics.class);
        GHRepositoryStatistics.ContributorStats contributorStats = mock(GHRepositoryStatistics.ContributorStats.class);
        GHUser user = mockUser("holly-test-holly");
        when(contributorStats.getAuthor()).thenReturn(user);
        PagedIterable<GHRepositoryStatistics.ContributorStats> iterableStats = MockHelper.mockPagedIterable(contributorStats);
        when(stats.getContributorStats()).thenReturn(iterableStats);
        when(repoMock.getStatistics()).thenReturn(stats);

    }

    @Test
    void changeToAnAllowedDirectoryShouldBeApproved() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        files:
                                            - ./src
                                      """);
            setupMockQueriesAndCommits(mocks);
            PagedIterable<GHPullRequestFileDetail> paths = MockHelper
                    .mockPagedIterable(MockHelper.mockGHPullRequestFileDetail("./src/innocuous.java"));
            when(pr(mocks).listFiles()).thenReturn(paths);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verify(pr(mocks)).listFiles();
                    verifyApproved(mocks);
                });
    }

    @Test
    void changeToAWildcardedDirectoryShouldBeApproved() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        files:
                                            - "*"
                                      """);
            setupMockQueriesAndCommits(mocks);
            PagedIterable<GHPullRequestFileDetail> paths = MockHelper
                    .mockPagedIterable(MockHelper.mockGHPullRequestFileDetail("./src/innocuous.java"));
            when(pr(mocks).listFiles()).thenReturn(paths);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verify(pr(mocks)).listFiles();
                    verifyApproved(mocks);
                });
    }

    @Test
    void changeToADirectoryWithNoRulesShouldBeSoftRejected() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        files:
                                            - ./src
                                      """);
            setupMockQueriesAndCommits(mocks);
            PagedIterable<GHPullRequestFileDetail> paths = MockHelper
                    .mockPagedIterable(MockHelper.mockGHPullRequestFileDetail("./github/important.yml"));
            when(pr(mocks).listFiles()).thenReturn(paths);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verify(pr(mocks)).listFiles();
                    verifyNotApproved(mocks);
                });
    }

    @Test
    void changeToAnAllowedAndUnlessedDirectoryShouldBeSoftRejected() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        files:
                                            - "*"
                                      unless:
                                         files:
                                           - ./github
                                      """);
            setupMockQueriesAndCommits(mocks);
            PagedIterable<GHPullRequestFileDetail> paths = MockHelper
                    .mockPagedIterable(MockHelper.mockGHPullRequestFileDetail("./github/important.yml"));
            when(pr(mocks).listFiles()).thenReturn(paths);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verify(pr(mocks), times(2)).listFiles();
                    verifyNotApproved(mocks);
                });
    }

    @Test
    void changeToAnAllowedDirectoryWithAnIrrelevantUnlessedDirectoryShouldBeAccepted() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        files:
                                            - "*"
                                      unless:
                                         files:
                                           - ./github
                                      """);
            setupMockQueriesAndCommits(mocks);
            PagedIterable<GHPullRequestFileDetail> paths = MockHelper
                    .mockPagedIterable(MockHelper.mockGHPullRequestFileDetail("./innocuous/important.yml"));
            when(pr(mocks).listFiles()).thenReturn(paths);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verify(pr(mocks), times(2)).listFiles();
                    verifyApproved(mocks);
                });
    }

    @Test
    void changeToAnAllowedFileShouldBeApproved() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        files:
                                            - "**/pom.xml"
                                      """);
            setupMockQueriesAndCommits(mocks);
            PagedIterable<GHPullRequestFileDetail> paths = MockHelper
                    .mockPagedIterable(MockHelper.mockGHPullRequestFileDetail("./innocuous/something/pom.xml"));
            when(pr(mocks).listFiles()).thenReturn(paths);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verify(pr(mocks)).listFiles();
                    verifyApproved(mocks);
                });
    }

    @Test
    void changeToAFileInAnAllowedDirectoryWithAnIrrelevantUnlessShouldBeAllowed() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        files:
                                          - ./src
                                      unless:
                                        files:
                                            - "**/bad.xml"
                                      """);
            setupMockQueriesAndCommits(mocks);
            PagedIterable<GHPullRequestFileDetail> paths = MockHelper
                    .mockPagedIterable(MockHelper.mockGHPullRequestFileDetail("./src/good.xml"));
            when(pr(mocks).listFiles()).thenReturn(paths);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verify(pr(mocks), times(2)).listFiles();
                    verifyApproved(mocks);
                });
    }

    @Test
    void changeToAnUnlessedFileInAnAllowedDirectoryShouldBeSoftRejected() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        files:
                                          - ./src
                                      unless:
                                        files:
                                            - "**/bad.xml"
                                      """);
            setupMockQueriesAndCommits(mocks);
            PagedIterable<GHPullRequestFileDetail> paths = MockHelper
                    .mockPagedIterable(MockHelper.mockGHPullRequestFileDetail("./src/bad.xml"));
            when(pr(mocks).listFiles()).thenReturn(paths);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verify(pr(mocks), times(2)).listFiles();
                    verifyNotApproved(mocks);
                });
    }

    @Test
    void changeFromAnUnknownUserShouldBeSoftRejected() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        users:
                                          minContributions: 5
                                      """);
            setupMockQueriesAndCommits(mocks);
            setupMockUsers(mocks);
        })
                .when().payloadFromClasspath("/workflow-unknown-contributor-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verifyNotApproved(mocks);
                });
    }

    @Test
    void changeFromANewishUserShouldBeSoftRejected() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        users:
                                          minContributions: 5
                                      """);
            setupMockQueriesAndCommits(mocks);
            setupMockUsers(mocks);
            GHRepositoryStatistics.ContributorStats contributorStats = mocks.repository("bot-playground").getStatistics()
                    .getContributorStats().iterator().next();
            when(contributorStats.getTotal()).thenReturn(1);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verifyNotApproved(mocks);
                });
    }

    @Test
    void changeFromAnEstablishedUserShouldBeAllowed() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        users:
                                          minContributions: 5
                              """);
            setupMockQueriesAndCommits(mocks);
            setupMockUsers(mocks);
            GHRepositoryStatistics.ContributorStats contributorStats = mocks.repository("bot-playground").getStatistics()
                    .getContributorStats().iterator().next();
            when(contributorStats.getTotal()).thenReturn(20);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verifyApproved(mocks);
                });
    }

    @Test
    void changeFromAnEstablishedUserToADangerousFileShouldBeSoftRejected() throws Exception {
        given().github(mocks -> {
            mocks.configFileFromString(
                    "quarkus-github-bot.yml",
                    """
                            features: [ APPROVE_WORKFLOWS ]
                            workflows:
                                  rules:
                                    - allow:
                                        users:
                                          minContributions: 5
                                      unless:
                                        files:
                                         - "**/bad.xml"
                              """);
            setupMockQueriesAndCommits(mocks);
            setupMockUsers(mocks);
            PagedIterable<GHPullRequestFileDetail> paths = MockHelper
                    .mockPagedIterable(MockHelper.mockGHPullRequestFileDetail("./src/bad.xml"));
            GHRepositoryStatistics.ContributorStats contributorStats = mocks.repository("bot-playground").getStatistics()
                    .getContributorStats().iterator().next();
            when(contributorStats.getTotal()).thenReturn(20);
        })
                .when().payloadFromClasspath("/workflow-approval-needed.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verifyApproved(mocks);
                });
    }

    @Test
    void workflowIsPreApprovedShouldDoNothing() throws Exception {
        given().github(mocks -> mocks.configFileFromString(
                "quarkus-github-bot.yml",
                """
                        features: [ APPROVE_WORKFLOWS ]
                        workflows:
                              rules:
                                - allow:
                                    files:
                                      - ./src
                                  unless:
                                    files:
                                      - "**/bad.xml"
                          """))
                .when().payloadFromClasspath("/workflow-from-committer.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    // No interactions expected, because the workflow is already in an active state
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void noRulesShouldDoNothing() throws Exception {
        given().github(mocks -> mocks.configFileFromString(
                "quarkus-github-bot.yml",
                """
                        features: [ APPROVE_WORKFLOWS, ALL ]
                        workflows:
                              rules:
                                """))
                .when().payloadFromClasspath("/workflow-from-committer.json")
                .event(GHEvent.WORKFLOW_RUN)
                .then().github(mocks -> {
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    private void verifyApproved(GitHubMockVerificationContext mocks) throws Exception {
        GHWorkflowRun workflow = mocks.ghObject(GHWorkflowRun.class, 2860832197l);
        verify(workflow).approve();

    }

    private void verifyNotApproved(GitHubMockVerificationContext mocks) throws Exception {
        GHWorkflowRun workflow = mocks.ghObject(GHWorkflowRun.class, 2860832197l);
        verify(workflow, never()).approve();

    }

    private GHPullRequest pr(GitHubMockSetupContext mocks) {
        return mocks.pullRequest(527350930);
    }

    private GHPullRequest pr(GitHubMockVerificationContext mocks) {
        return mocks.pullRequest(527350930);
    }

}
