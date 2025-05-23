package io.quarkus.bot.violation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.kohsuke.github.GHPullRequest;

import io.quarkus.bot.util.GHPullRequests;

/**
 * Detector for violations in pull request titles.
 */
class TitleViolationDetector implements ViolationDetector {

    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern ISSUE_PATTERN = Pattern.compile("#[0-9]+");
    private static final Pattern FIX_FEAT_CHORE = Pattern.compile("^(fix|chore|feat|docs|refactor)[(:].*");
    private static final List<String> UPPER_CASE_EXCEPTIONS = List.of("gRPC");

    @Override
    public List<EditorialViolation> detectViolations(GHPullRequest pullRequest) {
        String baseBranch = "";
        if (pullRequest.getBase() != null) {
            baseBranch = pullRequest.getBase().getRef();
        }

        String originalTitle = pullRequest.getTitle();
        String normalizedTitle = GHPullRequests.normalizeTitle(originalTitle, baseBranch);

        // we remove the potential version prefix before checking the editorial rules
        String title = GHPullRequests.dropVersionSuffix(normalizedTitle, baseBranch);

        return getTitleViolations(title);
    }

    private List<EditorialViolation> getTitleViolations(String title) {
        if (title == null || title.isEmpty()) {
            return List.of(EditorialViolation.TitleViolation("title should not be empty"));
        }
        List<EditorialViolation> violations = new ArrayList<>();

        if (title.endsWith(".")) {
            violations.add(EditorialViolation.TitleViolation("title should not end up with dot"));
        }
        if (title.endsWith("…")) {
            violations.add(EditorialViolation
                    .TitleViolation("title should not end up with ellipsis (make sure the title is complete)"));
        }
        if (SPACE_PATTERN.split(title.trim()).length < 2) {
            violations.add(
                    EditorialViolation.TitleViolation("title should count at least 2 words to describe the change properly"));
        }
        if (!Character.isDigit(title.codePointAt(0)) && !Character.isUpperCase(title.codePointAt(0))
                && !isUpperCaseException(title)) {
            violations.add(EditorialViolation
                    .TitleViolation("title should preferably start with an uppercase character (if it makes sense!)"));
        }
        if (ISSUE_PATTERN.matcher(title).find()) {
            violations.add(EditorialViolation
                    .TitleViolation("title should not contain an issue number (use `Fix #1234` in the description instead)"));
        }
        if (FIX_FEAT_CHORE.matcher(title).matches()) {
            violations.add(EditorialViolation
                    .TitleViolation("title should not start with chore/docs/feat/fix/refactor but be a proper sentence"));
        }

        return violations;
    }

    private boolean isUpperCaseException(String title) {
        for (String exception : UPPER_CASE_EXCEPTIONS) {
            if (title.toLowerCase(Locale.ROOT).startsWith(exception.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
