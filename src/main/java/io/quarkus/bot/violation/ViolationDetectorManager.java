package io.quarkus.bot.violation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.kohsuke.github.GHPullRequest;

/**
 * Manager for all violation detectors.
 */
@Singleton
public class ViolationDetectorManager {
    private final List<ViolationDetector> detectors;

    public ViolationDetectorManager() {
        this.detectors = new ArrayList<>();
        // Register all detectors
        this.detectors.add(new TitleViolationDetector());
        this.detectors.add(new BodyViolationDetector());
    }

    public List<EditorialViolation> detectViolations(GHPullRequest pullRequest) throws IOException {
        List<EditorialViolation> allViolations = new ArrayList<>();

        for (ViolationDetector detector : detectors) {
            allViolations.addAll(detector.detectViolations(pullRequest));
        }

        return allViolations;
    }

    public List<String> getViolationMessages(List<EditorialViolation> violations, EditorialViolation.ViolationType type) {
        return violations.stream()
                .filter(v -> v.type() == type)
                .map(EditorialViolation::message)
                .collect(Collectors.toList());
    }

    public List<String> getTitleViolationMessages(List<EditorialViolation> violations) {
        return getViolationMessages(violations, EditorialViolation.ViolationType.TITLE);
    }

    public List<String> getBodyViolationMessages(List<EditorialViolation> violations) {
        return getViolationMessages(violations, EditorialViolation.ViolationType.BODY);
    }
}
