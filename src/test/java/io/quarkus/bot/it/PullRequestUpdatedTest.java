package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static io.quarkus.bot.it.util.GHPullRequestsTest.FEATURES_CHECK_EDITORIAL_RULES;
import static io.quarkus.bot.util.Strings.EDITORIAL_RULES_COMMENT_MARKER;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class PullRequestUpdatedTest {

    @Test
    void deleteCommentIfViolationsAreGone() throws IOException {
        // Create a mock comment with the editorial rules marker
        GHIssueComment comment = mock(GHIssueComment.class);
        when(comment.getBody()).thenReturn("Violation detected ! " + EDITORIAL_RULES_COMMENT_MARKER);

        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml").fromString(FEATURES_CHECK_EDITORIAL_RULES);

            GHPullRequest pullRequest = mocks.pullRequest(527350930);
            when(pullRequest.getComments()).thenReturn(Collections.singletonList(comment));
        })
                .when().payloadFromClasspath("/pullrequest-edited-violations-fixed.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(comment).delete();
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void updateCommentIfViolationsStillExist() throws IOException {
        // Create a mock comment with the editorial rules marker
        GHIssueComment comment = mock(GHIssueComment.class);
        when(comment.getBody()).thenReturn("Old violation message " + EDITORIAL_RULES_COMMENT_MARKER);

        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml").fromString(FEATURES_CHECK_EDITORIAL_RULES);

            GHPullRequest pullRequest = mocks.pullRequest(527350930);
            when(pullRequest.getComments()).thenReturn(Collections.singletonList(comment));
        })
                .when().payloadFromClasspath("/pullrequest-edited-violations-persisting.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(comment, never()).delete();
                    verify(comment).update(org.mockito.ArgumentMatchers.contains(EDITORIAL_RULES_COMMENT_MARKER));
                });
    }

    @Test
    void DoNotDeleteOnUpdateIfViolationStillExist() throws IOException {
        GHIssueComment comment = mock(GHIssueComment.class);

        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml").fromString(FEATURES_CHECK_EDITORIAL_RULES);

            // Mock the pull request to return an empty list of comments
            GHPullRequest pullRequest = mocks.pullRequest(527350930);
            when(pullRequest.getComments()).thenReturn(Collections.emptyList());
        })
                .when().payloadFromClasspath("/pullrequest-edited-violations-persisting.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> verify(comment, never()).delete());
    }

    @Test
    void addCommentIfViolationsExistAndNoCommentPresent() throws IOException {
        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml").fromString(FEATURES_CHECK_EDITORIAL_RULES);

            GHPullRequest pullRequest = mocks.pullRequest(527350930);
            when(pullRequest.getComments()).thenReturn(Collections.emptyList());
        })
                .when().payloadFromClasspath("/pullrequest-edited-violations-persisting.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(527350930))
                            .comment(org.mockito.ArgumentMatchers.contains(EDITORIAL_RULES_COMMENT_MARKER));
                });
    }
}
