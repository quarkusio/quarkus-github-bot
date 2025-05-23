package io.quarkus.bot.violation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.github.GHPullRequest;

import io.quarkus.bot.util.PullRequestFilesMatcher;
import io.quarkus.bot.util.Strings;

/**
 * Detector for violations in pull request descriptions (body).
 */
class BodyViolationDetector implements ViolationDetector {

    private static final List<String> BOMS = List.of("bom/application/pom.xml");
    private static final List<String> DOC_CHANGES = List.of("docs/src/main/asciidoc/", "README.md", "LICENSE",
            "CONTRIBUTING.md");

    @Override
    public List<EditorialViolation> detectViolations(GHPullRequest pullRequest) throws IOException {
        String body = pullRequest.getBody();
        List<EditorialViolation> violations = new ArrayList<>();
        if (Strings.isBlank(body) && isMeaningfulPullRequest(pullRequest)) {
            violations.add(EditorialViolation.BodyViolation(
                    "description should not be empty, describe your intent or provide links to the issues this PR is fixing (using `Fixes #NNNNN`) or changelogs"));
        }
        return violations;
    }

    private boolean isMeaningfulPullRequest(GHPullRequest pullRequest) throws IOException {
        // Note: these rules will have to be adjusted depending on how it goes
        // we don't want to annoy people fixing a typo or require a description for a one liner explained in the title

        // if we have more than one commit, then it's meaningful
        if (pullRequest.getCommits() > 1) {
            return true;
        }

        PullRequestFilesMatcher filesMatcher = new PullRequestFilesMatcher(pullRequest);

        // for changes to the BOM, we are stricter
        if (filesMatcher.changedFilesMatch(BOMS)) {
            return true;
        }

        // for one liner/two liners, let's be a little more lenient
        if (pullRequest.getAdditions() <= 2 && pullRequest.getDeletions() <= 2) {
            return false;
        }

        // let's be a little more flexible for doc changes
        if (filesMatcher.changedFilesOnlyMatch(DOC_CHANGES)
                && pullRequest.getAdditions() <= 10 && pullRequest.getDeletions() <= 10) {
            return false;
        }

        return true;
    }
}
