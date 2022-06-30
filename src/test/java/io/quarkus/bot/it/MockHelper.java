package io.quarkus.bot.it;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;

import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

public class MockHelper {

    public static GHPullRequestFileDetail mockGHPullRequestFileDetail(String filename) {
        GHPullRequestFileDetail mock = mock(GHPullRequestFileDetail.class);
        when(mock.getFilename()).thenReturn(filename);
        return mock;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> PagedIterable<T> mockPagedIterable(T... contentMocks) {
        PagedIterable<T> iterableMock = mock(PagedIterable.class);
        when(iterableMock.iterator()).thenAnswer(ignored -> {
            PagedIterator<T> iteratorMock = mock(PagedIterator.class);
            Iterator<T> actualIterator = List.of(contentMocks).iterator();
            when(iteratorMock.next()).thenAnswer(ignored2 -> actualIterator.next());
            when(iteratorMock.hasNext()).thenAnswer(ignored2 -> actualIterator.hasNext());
            return iteratorMock;
        });
        return iterableMock;
    }

}
