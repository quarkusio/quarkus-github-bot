package io.quarkus.bot.violation;

import java.io.IOException;
import java.util.List;

import org.kohsuke.github.GHPullRequest;

/**
 * Interface for detecting editorial rule violations in pull requests.
 */
interface ViolationDetector {

    List<EditorialViolation> detectViolations(GHPullRequest pullRequest) throws IOException;
}