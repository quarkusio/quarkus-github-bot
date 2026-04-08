package io.quarkus.bot.it;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

public class MockHelper {

    public static GHPullRequestFileDetail mockGHPullRequestFileDetail(String filename) {
        GHPullRequestFileDetail mock = mock(GHPullRequestFileDetail.class);
        lenient().when(mock.getFilename()).thenReturn(filename);
        return mock;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> PagedIterable<T> mockPagedIterable(T... contentMocks) {
        return new PagedIterable<>() {
            @Override
            public PagedIterator<T> _iterator(int pageSize) {
                return mockPagedIterator(contentMocks);
            }

            @Override
            public List<T> toList() throws IOException {
                return List.of(contentMocks);
            }
        };
    }

    private static <T> PagedIterator<T> mockPagedIterator(T... contentMocks) {
        PagedIterator<T> iteratorMock = mock(PagedIterator.class);
        Iterator<T> actualIterator = List.of(contentMocks).iterator();
        when(iteratorMock.next()).thenAnswer(ignored2 -> actualIterator.next());
        lenient().when(iteratorMock.hasNext()).thenAnswer(ignored2 -> actualIterator.hasNext());
        return iteratorMock;
    }

    public static GHUser mockUser(String login) {
        GHUser user = mock(GHUser.class);
        lenient().when(user.getLogin()).thenReturn(login);
        return user;
    }
}
