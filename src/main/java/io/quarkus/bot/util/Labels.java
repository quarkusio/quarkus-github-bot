package io.quarkus.bot.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.kohsuke.github.GHLabel;

public class Labels {

    /**
     * We cannot add more than 100 labels and we have some other automatic labels such as kind/bug.
     */
    private static final int LABEL_SIZE_LIMIT = 95;

    public static final String AREA_PREFIX = "area/";
    public static final String AREA_INFRA = "area/infra";
    public static final String CI_PREFIX = "ci/";
    public static final String TRIAGE_INVALID = "triage/invalid";
    public static final String TRIAGE_NEEDS_TRIAGE = "triage/needs-triage";
    public static final String TRIAGE_WAITING_FOR_CI = "triage/waiting-for-ci";
    public static final String TRIAGE_QE = "triage/qe?";
    public static final String TRIAGE_BACKPORT_PREFIX = "triage/backport";

    public static final String KIND_BUG = "kind/bug";
    public static final String KIND_ENHANCEMENT = "kind/enhancement";
    public static final String KIND_NEW_FEATURE = "kind/new-feature";
    public static final String KIND_COMPONENT_UPGRADE = "kind/component-upgrade";
    public static final String KIND_BUGFIX = "kind/bugfix";

    public static final String KIND_EXTENSION_PROPOSAL = "kind/extension-proposal";
    public static final Set<String> KIND_LABELS = Set.of(KIND_BUG, KIND_ENHANCEMENT, KIND_NEW_FEATURE, KIND_EXTENSION_PROPOSAL);

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

    public static boolean matchesName(Collection<String> labels, String labelCandidate) {
        for (String label : labels) {
            if (label.equals(labelCandidate)) {
                return true;
            }
        }

        return false;
    }

    public static boolean matches(Collection<GHLabel> labels, String labelCandidate) {
        for (GHLabel label : labels) {
            if (label.getName().equals(labelCandidate)) {
                return true;
            }
        }

        return false;
    }
}
