package io.quarkus.bot.util;

import java.util.regex.Pattern;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

public final class GHPullRequests {

    private static final Pattern CLEAN_VERSION_PATTERN = Pattern.compile("^\\[?\\(?[0-9]+\\.[0-9]+\\]?\\)?(?![\\.0-9])[ -]*");

    public static boolean hasLabel(GHPullRequest pullRequest, String labelName) {
        for (GHLabel label : pullRequest.getLabels()) {
            if (labelName.equals(label.getName())) {
                return true;
            }
        }
        return false;
    }

    public static String dropVersionSuffix(String title, String branch) {
        if (title == null || title.isBlank()) {
            return title;
        }
        if (!Branches.isVersionBranch(branch)) {
            return title;
        }

        return CLEAN_VERSION_PATTERN.matcher(title).replaceFirst("");
    }

    public static String normalizeTitle(String title, String branch) {
        if (title == null || title.isBlank()) {
            return title;
        }
        if (!Branches.isVersionBranch(branch)) {
            return title;
        }

        return "[" + branch + "] " + dropVersionSuffix(title, branch);
    }
}
