package io.quarkus.bot.retest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.inject.Singleton;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHWorkflowRun;

import io.quarkus.test.Mock;

@Mock
@Singleton
/**
 * Test double that records workflow runs requested for rerun.
 */
public class CapturingFailedJobsRerunner implements FailedJobsRerunner {

    private final CopyOnWriteArrayList<Long> rerunWorkflowRunIds = new CopyOnWriteArrayList<>();

    @Override
    public void rerunFailedJobs(GHEventPayload.IssueComment issueCommentPayload, GHWorkflowRun workflowRun) {
        rerunWorkflowRunIds.add(workflowRun.getId());
    }

    public List<Long> rerunWorkflowRunIds() {
        return List.copyOf(rerunWorkflowRunIds);
    }

    public void reset() {
        rerunWorkflowRunIds.clear();
    }
}
