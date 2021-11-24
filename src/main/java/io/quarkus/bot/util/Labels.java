package io.quarkus.bot.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class Labels {

    /**
     * We cannot add more than 100 labels and we have some other automatic labels such as kind/bug.
     */
    private static final int LABEL_SIZE_LIMIT = 95;

    public static final String AREA_PREFIX = "area/";
    public static final String AREA_INFRA = "area/infra";
    public static final String TRIAGE_INVALID = "triage/invalid";
    public static final String TRIAGE_NEEDS_TRIAGE = "triage/needs-triage";
    public static final String TRIAGE_WAITING_FOR_CI = "triage/waiting-for-ci";
    public static final String TRIAGE_QE = "triage/qe?";
    public static final String TRIAGE_BACKPORT_PREFIX = "triage/backport";

    private Labels() {
    }

    public static boolean hasAreaLabels(Set<String> labels) {
        for (String label : labels) {
            if (label.startsWith(Labels.AREA_PREFIX)) {
                return true;
            }
        }

        return false;
    }

    public static Collection<String> limit(Set<String> labels) {
        if (labels.size() <= LABEL_SIZE_LIMIT) {
            return labels;
        }

        return new ArrayList<>(labels).subList(0, LABEL_SIZE_LIMIT);
    }
}
