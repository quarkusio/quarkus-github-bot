package io.quarkus.bot.workflow;

import org.kohsuke.github.GHWorkflowJob;

import java.util.Comparator;

public class GHWorkflowJobComparator implements Comparator<GHWorkflowJob> {

    public static final GHWorkflowJobComparator INSTANCE = new GHWorkflowJobComparator();

    private static final String INITIAL_JDK_PREFIX = "Initial JDK ";

    @Override
    public int compare(GHWorkflowJob o1, GHWorkflowJob o2) {
        if (o1.getName().startsWith(INITIAL_JDK_PREFIX) && !o2.getName().startsWith(INITIAL_JDK_PREFIX)) {
            return -1;
        }
        if (!o1.getName().startsWith(INITIAL_JDK_PREFIX) && o2.getName().startsWith(INITIAL_JDK_PREFIX)) {
            return 1;
        }

        return o1.getName().compareTo(o2.getName());
    }

}
