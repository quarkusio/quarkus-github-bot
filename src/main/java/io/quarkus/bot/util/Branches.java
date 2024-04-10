package io.quarkus.bot.util;

import java.util.regex.Pattern;

public final class Branches {

    private static final Pattern VERSION_BRANCH_PATTERN = Pattern.compile("[0-9]+\\.[0-9]+");

    private Branches() {
    }

    public static boolean isVersionBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            return false;
        }

        return VERSION_BRANCH_PATTERN.matcher(branch).matches();
    }
}
