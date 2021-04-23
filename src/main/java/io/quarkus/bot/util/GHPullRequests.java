package io.quarkus.bot.util;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

public final class GHPullRequests {

    public static boolean hasLabel(GHPullRequest pullRequest, String labelName) {
        for (GHLabel label : pullRequest.getLabels()) {
            if (labelName.equals(label.getName())) {
                return true;
            }
        }
        return false;
    }
}
