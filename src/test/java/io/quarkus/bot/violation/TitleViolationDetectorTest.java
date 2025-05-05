package io.quarkus.bot.violation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHPullRequest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TitleViolationDetectorTest {

    @Mock
    private GHPullRequest pullRequest;

    private TitleViolationDetector detector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        detector = new TitleViolationDetector();
    }

    @Test
    void testEmptyTitle() {
        when(pullRequest.getTitle()).thenReturn("");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertEquals(1, violations.size());
        assertEquals(EditorialViolation.ViolationType.TITLE, violations.get(0).type());
        assertTrue(violations.get(0).message().contains("title should not be empty"));
    }

    @Test
    void testTitleWithDot() {
        when(pullRequest.getTitle()).thenReturn("This is a title with a dot.");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertTrue(violations.stream()
                .anyMatch(v -> v.message().contains("title should not end up with dot")));
    }

    @Test
    void testTitleWithEllipsis() {
        when(pullRequest.getTitle()).thenReturn("This is a title with ellipsis…");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertTrue(violations.stream()
                .anyMatch(v -> v.message().contains("title should not end up with ellipsis")));
    }

    @Test
    void testTitleWithTooFewWords() {
        when(pullRequest.getTitle()).thenReturn("Title");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertTrue(violations.stream()
                .anyMatch(v -> v.message().contains("title should count at least 2 words")));
    }

    @Test
    void testTitleNotStartingWithUppercase() {
        when(pullRequest.getTitle()).thenReturn("lowercase title");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertTrue(violations.stream()
                .anyMatch(v -> v.message().contains("title should preferably start with an uppercase character")));
    }

    @Test
    void testTitleWithIssueNumber() {
        when(pullRequest.getTitle()).thenReturn("Fix #1234 issue");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertTrue(violations.stream()
                .anyMatch(v -> v.message().contains("title should not contain an issue number")));
    }

    @Test
    void testTitleStartingWithFixOrFeat() {
        when(pullRequest.getTitle()).thenReturn("fix: something");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertTrue(violations.stream()
                .anyMatch(v -> v.message().contains("title should not start with chore/docs/feat/fix/refactor")));
    }

    @Test
    void testValidTitle() {
        when(pullRequest.getTitle()).thenReturn("This is a valid title");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testUpperCaseException() {
        when(pullRequest.getTitle()).thenReturn("gRPC implementation");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testLowercaseIsAlsoAnException() {
        when(pullRequest.getTitle()).thenReturn("change implementation");

        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        assertTrue(violations.stream()
                .anyMatch(v -> v.message().contains("title should preferably start with an uppercase character")));
    }
}
