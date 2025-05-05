package io.quarkus.bot.violation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHPullRequest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class BodyViolationDetectorTest {

    @Mock
    private GHPullRequest pullRequest;

    private BodyViolationDetector detector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        detector = new BodyViolationDetector();
    }

    @Test
    void testEmptyBodyWithMultipleCommits() throws IOException {
        when(pullRequest.getBody()).thenReturn("");
        when(pullRequest.getCommits()).thenReturn(2); // More than 1 commit makes it meaningful

        // Execute
        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        // Verify
        assertEquals(1, violations.size());
        assertEquals(EditorialViolation.ViolationType.BODY, violations.get(0).type());
        assertTrue(violations.get(0).message().contains("description should not be empty"));
    }

    @Test
    void testEmptyBodyWithSingleCommitAndSmallChanges() throws IOException {
        // Setup for a non-meaningful PR with a single commit and small changes
        when(pullRequest.getBody()).thenReturn("");
        when(pullRequest.getCommits()).thenReturn(1);
        when(pullRequest.getAdditions()).thenReturn(1); // Only 1 addition
        when(pullRequest.getDeletions()).thenReturn(1); // Only 1 deletion

        // Execute
        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        // Verify - should be no violations for non-meaningful PRs
        assertTrue(violations.isEmpty(), "No violations should be reported for minor changes");
    }

    @Test
    void testNonEmptyBody() throws IOException {
        // Setup for a meaningful PR with a non-empty body
        when(pullRequest.getBody()).thenReturn("This is a description of the changes");
        when(pullRequest.getCommits()).thenReturn(2); // Meaningful PR

        // Execute
        List<EditorialViolation> violations = detector.detectViolations(pullRequest);

        // Verify - should be no violations for non-empty body
        assertTrue(violations.isEmpty(), "No violations should be reported for non-empty body");
    }
}