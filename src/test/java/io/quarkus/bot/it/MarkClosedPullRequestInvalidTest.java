package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRunQueryBuilder;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Answers;

import io.quarkiverse.githubapp.testing.GitHubAppTestingResource;
import io.quarkus.bot.util.Labels;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(GitHubAppTestingResource.class)
public class MarkClosedPullRequestInvalidTest {

    @Test
    void handleLabels() throws IOException {
        given().github(mocks -> {
            // this is necessary because this payload also triggers CancelWorkflowOnClosedPullRequest
            GHRepository repoMock = mocks.repository("GitHubTestAppRepo");
            GHWorkflowRunQueryBuilder workflowRunQueryBuilderMock = mock(GHWorkflowRunQueryBuilder.class,
                    withSettings().defaultAnswer(Answers.RETURNS_SELF));
            when(repoMock.queryWorkflowRuns())
                    .thenReturn(workflowRunQueryBuilderMock);
            PagedIterable<GHWorkflowRun> iterableMock = mockPagedIterable(Collections.emptyList());
            when(workflowRunQueryBuilderMock.list())
                    .thenReturn(iterableMock);
        })
                .when()
                .payloadFromClasspath("/pullrequest-closed.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(691467750))
                            .addLabels(Labels.TRIAGE_INVALID);
                    verify(mocks.pullRequest(691467750))
                            .removeLabel(Labels.TRIAGE_BACKPORT_PREFIX);
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> PagedIterable<T> mockPagedIterable(List<T> contentMocks) {
        PagedIterable<T> iterableMock = mock(PagedIterable.class);
        Iterator<T> actualIterator = contentMocks.iterator();
        PagedIterator<T> iteratorMock = mock(PagedIterator.class);
        when(iterableMock.iterator()).thenAnswer(ignored -> iteratorMock);
        when(iteratorMock.next()).thenAnswer(ignored -> actualIterator.next());
        when(iteratorMock.hasNext()).thenAnswer(ignored -> actualIterator.hasNext());
        return iterableMock;
    }
}
