package io.quarkus.bot.retest;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHWorkflowRun;

interface FailedJobsRerunner {

    /**
     * Retriggers only the failed jobs for the given workflow run.
     */
    void rerunFailedJobs(GHEventPayload.IssueComment issueCommentPayload, GHWorkflowRun workflowRun);
}
