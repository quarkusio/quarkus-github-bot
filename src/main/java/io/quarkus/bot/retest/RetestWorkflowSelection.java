package io.quarkus.bot.retest;

import java.util.List;

import org.kohsuke.github.GHWorkflowRun;

/**
 * The result of selecting a workflow runs for a retest command execution.
 */
record RetestWorkflowSelection(List<GHWorkflowRun> eligibleRuns, NoEligibleReason noEligibleReason) {

    enum NoEligibleReason {
        NO_WORKFLOW_RUNS_FOR_HEAD,
        LATEST_RUNS_NOT_COMPLETED,
        LATEST_RUNS_GREEN,
        NO_RERUNNABLE_FAILED_JOBS
    }
}
