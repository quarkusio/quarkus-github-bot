package io.quarkus.bot.util;

import java.io.IOException;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

public final class GHIssues {

    public static boolean hasLabel(GHIssue issue, String labelName) throws IOException {
        for (GHLabel label : issue.getLabels()) {
            if (labelName.equals(label.getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAreaLabel(GHIssue issue) throws IOException {
        for (GHLabel label : issue.getLabels()) {
            if (label.getName().startsWith(Labels.AREA_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private GHIssues() {
    }
}
