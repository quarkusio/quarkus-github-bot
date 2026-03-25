package io.quarkus.bot.retest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command-specific failure with a user-facing message suitable for issue comments.
 */
class RetestCommandException extends RuntimeException {

    private final String userMessage;

    private RetestCommandException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
    }

    private RetestCommandException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
    }

    String userMessage() {
        return userMessage;
    }

    static RetestCommandException featureDisabled() {
        return new RetestCommandException(":warning: Pull request workflow retest is disabled for this repository.");
    }

    static RetestCommandException ambiguousPullRequestHead() {
        return new RetestCommandException(
                ":warning: Multiple pull requests share the same head branch and commit, so retest was skipped.");
    }

    static RetestCommandException pullRequestNotOpen() {
        return new RetestCommandException(":warning: Retest is only available on open pull requests.");
    }

    static RetestCommandException noEligibleWorkflowRuns(RetestWorkflowSelection.NoEligibleReason reason) {
        return switch (reason) {
            case NO_WORKFLOW_RUNS_FOR_HEAD -> new RetestCommandException(
                    ":warning: No workflow runs matched the latest head for this pull request.");
            case LATEST_RUNS_NOT_COMPLETED -> new RetestCommandException(
                    ":warning: The latest workflow runs for this pull request are not completed yet.");
            case LATEST_RUNS_GREEN -> new RetestCommandException(
                    ":white_check_mark: The latest workflow runs for this pull request are already green.");
            case NO_RERUNNABLE_FAILED_JOBS -> new RetestCommandException(
                    ":warning: The latest workflow runs for this pull request do not contain rerunnable failed jobs.");
        };
    }

    static RetestCommandException unableToInspectWorkflowRuns(Throwable cause) {
        return new RetestCommandException(":rotating_light: Unable to inspect workflow runs for this pull request.", cause);
    }

    static RetestCommandException partialRerunFailure(List<Long> startedWorkflowRunIds, long failedWorkflowRunId,
            Throwable cause) {
        String startedRuns = startedWorkflowRunIds.stream()
                .map(workflowRunId -> "#" + workflowRunId)
                .collect(Collectors.joining(", "));
        return new RetestCommandException(
                ":warning: Retest was already started for workflow runs " + startedRuns
                        + ", but retriggering workflow run #" + failedWorkflowRunId
                        + " failed. Check the already started reruns before retrying.",
                cause);
    }

    static RetestCommandException rerunFailedJobsFailed(long workflowRunId, Integer statusCode, Throwable cause) {
        StringBuilder message = new StringBuilder(
                ":rotating_light: Unable to retrigger failed jobs for workflow run #" + workflowRunId);
        if (statusCode != null) {
            message.append(" (GitHub API status ").append(statusCode).append(")");
        }
        message.append(".");
        return new RetestCommandException(message.toString(), cause);
    }
}
