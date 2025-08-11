package io.quarkus.bot.violation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHPullRequest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ViolationDetectorManagerTest {

    @Mock
    private GHPullRequest pullRequest;

    private ViolationDetectorManager manager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        manager = new ViolationDetectorManager();
    }

    @Test
    void testDetectViolations() throws IOException {
        when(pullRequest.getTitle()).thenReturn("title with a dot.");
        when(pullRequest.getBody()).thenReturn("");
        when(pullRequest.getCommits()).thenReturn(2); // Make it a "meaningful" PR

        List<EditorialViolation> violations = manager.detectViolations(pullRequest);

        // We should have at least one title violation and one body violation
        assertTrue(violations.size() >= 2);
        assertTrue(violations.stream().anyMatch(v -> v.type() == EditorialViolation.ViolationType.TITLE));
        assertTrue(violations.stream().anyMatch(v -> v.type() == EditorialViolation.ViolationType.BODY));
    }

    @Test
    void testGetTitleViolationMessages() {
        // Create some test violations
        EditorialViolation titleViolation1 = EditorialViolation.TitleViolation("Title error 1");
        EditorialViolation titleViolation2 = EditorialViolation.TitleViolation("Title error 2");
        EditorialViolation bodyViolation = EditorialViolation.BodyViolation("Body error");

        List<EditorialViolation> violations = Arrays.asList(titleViolation1, titleViolation2, bodyViolation);

        // Test getting title violations
        List<String> titleMessages = manager.getTitleViolationMessages(violations);
        assertEquals(2, titleMessages.size());
        assertTrue(titleMessages.contains("Title error 1"));
        assertTrue(titleMessages.contains("Title error 2"));
    }

    @Test
    void testGetBodyViolationMessages() {
        EditorialViolation titleViolation = EditorialViolation.TitleViolation("Title error");
        EditorialViolation bodyViolation1 = EditorialViolation.BodyViolation("Body error 1");
        EditorialViolation bodyViolation2 = EditorialViolation.BodyViolation("Body error 2");

        List<EditorialViolation> violations = Arrays.asList(titleViolation, bodyViolation1, bodyViolation2);

        List<String> bodyMessages = manager.getBodyViolationMessages(violations);
        assertEquals(2, bodyMessages.size());
        assertTrue(bodyMessages.contains("Body error 1"));
        assertTrue(bodyMessages.contains("Body error 2"));
    }

}