package io.quarkus.bot.retest;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.PagedIterable;

import io.quarkus.bot.util.GHPullRequests;

/**
 * Selects the latest rerunnable workflow runs for a pull request head.
 */
@Singleton
class RetestWorkflowRunSelector {

    private static final int WORKFLOW_RUN_PAGE_SIZE = 30;
    private static final String PULL_REQUEST_TARGET_EVENT = "pull_request_target";

    private static final Set<GHWorkflowRun.Conclusion> FAILED_JOB_CONCLUSIONS = EnumSet.of(
            GHWorkflowRun.Conclusion.FAILURE,
            GHWorkflowRun.Conclusion.TIMED_OUT,
            GHWorkflowRun.Conclusion.STARTUP_FAILURE);

    RetestWorkflowSelection selectWorkflowRuns(GHPullRequest pullRequest) throws IOException {
        GHCommitPointer pullRequestHead = pullRequest.getHead();
        GHRepository pullRequestHeadRepository = pullRequestHead.getRepository();
        List<GHWorkflowRun> latestRuns = new ArrayList<>(
                collectLatestWorkflowRuns(pullRequest, pullRequestHead, pullRequestHeadRepository).values());
        List<AnalyzedWorkflowRun> analyzedRuns = analyzeRuns(latestRuns);
        List<GHWorkflowRun> eligibleRuns = eligibleRuns(analyzedRuns);

        eligibleRuns.sort(Comparator.comparingLong(GHWorkflowRun::getId).reversed());
        return new RetestWorkflowSelection(eligibleRuns, noEligibleReason(analyzedRuns, eligibleRuns));
    }

    private static Map<String, GHWorkflowRun> collectLatestWorkflowRuns(GHPullRequest pullRequest,
            GHCommitPointer pullRequestHead,
            GHRepository pullRequestHeadRepository) throws IOException {
        Map<String, GHWorkflowRun> latestWorkflowRuns = new LinkedHashMap<>();
        collectLatestWorkflowRuns(latestWorkflowRuns, pullRequest, pullRequestHead, pullRequestHeadRepository, "pull_request");
        collectLatestWorkflowRuns(latestWorkflowRuns, pullRequest, pullRequestHead, pullRequestHeadRepository,
                PULL_REQUEST_TARGET_EVENT);
        return latestWorkflowRuns;
    }

    private static void collectLatestWorkflowRuns(Map<String, GHWorkflowRun> latestWorkflowRuns,
            GHPullRequest pullRequest,
            GHCommitPointer pullRequestHead,
            GHRepository pullRequestHeadRepository,
            String event) throws IOException {
        PagedIterable<GHWorkflowRun> workflowRuns = pullRequest.getRepository().queryWorkflowRuns()
                .headSha(pullRequestHead.getSha())
                .event(event)
                .list()
                .withPageSize(WORKFLOW_RUN_PAGE_SIZE);

        for (GHWorkflowRun workflowRun : workflowRuns) {
            if (!matchesPullRequestHead(workflowRun, pullRequestHead, pullRequestHeadRepository)) {
                continue;
            }

            List<GHPullRequest> associatedPullRequests = workflowRun.getPullRequests();
            if (associatedPullRequests != null && !associatedPullRequests.isEmpty()
                    && !isAssociatedWithCurrentPullRequest(associatedPullRequests, pullRequest)) {
                continue;
            }
            String workflowIdentity = workflowIdentity(workflowRun, event);
            latestWorkflowRuns.merge(workflowIdentity, workflowRun, RetestWorkflowRunSelector::latestWorkflowRun);
        }
    }

    private static boolean matchesPullRequestHead(GHWorkflowRun workflowRun, GHCommitPointer pullRequestHead,
            GHRepository pullRequestHeadRepository) {
        if (!pullRequestHead.getSha().equals(workflowRun.getHeadSha())) {
            return false;
        }
        if (!pullRequestHead.getRef().equals(workflowRun.getHeadBranch())) {
            return false;
        }
        return GHPullRequests.isSameRepository(pullRequestHeadRepository, workflowRun.getHeadRepository());
    }

    private static boolean isAssociatedWithCurrentPullRequest(List<GHPullRequest> associatedPullRequests,
            GHPullRequest pullRequest) {
        if (associatedPullRequests == null || associatedPullRequests.isEmpty()) {
            return false;
        }

        return associatedPullRequests.stream()
                .anyMatch(associatedPullRequest -> associatedPullRequest.getNumber() == pullRequest.getNumber());
    }

    private static GHWorkflowRun latestWorkflowRun(GHWorkflowRun left, GHWorkflowRun right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }

        int runNumberComparison = Long.compare(left.getRunNumber(), right.getRunNumber());
        if (runNumberComparison != 0) {
            return runNumberComparison > 0 ? left : right;
        }

        int runAttemptComparison = Long.compare(left.getRunAttempt(), right.getRunAttempt());
        if (runAttemptComparison != 0) {
            return runAttemptComparison > 0 ? left : right;
        }

        return left.getId() >= right.getId() ? left : right;
    }

    private static List<AnalyzedWorkflowRun> analyzeRuns(List<GHWorkflowRun> latestRuns) {
        List<AnalyzedWorkflowRun> analyzedRuns = new ArrayList<>(latestRuns.size());
        for (GHWorkflowRun workflowRun : latestRuns) {
            analyzedRuns.add(analyzeRun(workflowRun));
        }
        return analyzedRuns;
    }

    private static AnalyzedWorkflowRun analyzeRun(GHWorkflowRun workflowRun) {
        boolean completed = workflowRun.getStatus() == GHWorkflowRun.Status.COMPLETED;
        boolean hasFailedLatestJob = completed
                && FAILED_JOB_CONCLUSIONS.contains(workflowRun.getConclusion())
                && hasFailedLatestJob(workflowRun);
        return new AnalyzedWorkflowRun(workflowRun, completed, hasFailedLatestJob);
    }

    private static List<GHWorkflowRun> eligibleRuns(List<AnalyzedWorkflowRun> analyzedRuns) {
        List<GHWorkflowRun> eligibleRuns = new ArrayList<>();
        for (AnalyzedWorkflowRun analyzedRun : analyzedRuns) {
            if (analyzedRun.completed() && analyzedRun.hasFailedLatestJob()) {
                eligibleRuns.add(analyzedRun.workflowRun());
            }
        }
        return eligibleRuns;
    }

    private static RetestWorkflowSelection.NoEligibleReason noEligibleReason(List<AnalyzedWorkflowRun> analyzedRuns,
            List<GHWorkflowRun> eligibleRuns) {
        if (!eligibleRuns.isEmpty()) {
            return null;
        }
        if (analyzedRuns.isEmpty()) {
            return RetestWorkflowSelection.NoEligibleReason.NO_WORKFLOW_RUNS_FOR_HEAD;
        }
        for (AnalyzedWorkflowRun analyzedRun : analyzedRuns) {
            if (!analyzedRun.completed()) {
                return RetestWorkflowSelection.NoEligibleReason.LATEST_RUNS_NOT_COMPLETED;
            }
        }
        for (AnalyzedWorkflowRun analyzedRun : analyzedRuns) {
            if (!analyzedRun.hasFailedLatestJob()
                    && analyzedRun.workflowRun().getConclusion() == GHWorkflowRun.Conclusion.SUCCESS) {
                continue;
            }
            return RetestWorkflowSelection.NoEligibleReason.NO_RERUNNABLE_FAILED_JOBS;
        }

        return RetestWorkflowSelection.NoEligibleReason.LATEST_RUNS_GREEN;
    }

    private static boolean hasFailedLatestJob(GHWorkflowRun workflowRun) {
        for (GHWorkflowJob workflowJob : workflowRun.listJobs()) {
            if (FAILED_JOB_CONCLUSIONS.contains(workflowJob.getConclusion())) {
                return true;
            }
        }

        return false;
    }

    private static String workflowIdentity(GHWorkflowRun workflowRun, String event) {
        if (workflowRun.getWorkflowId() > 0) {
            return "workflow-id:" + workflowRun.getWorkflowId() + ":event:" + event;
        }

        URL workflowUrl = workflowRun.getWorkflowUrl();
        if (workflowUrl != null) {
            return "workflow-url:" + workflowUrl + ":event:" + event;
        }

        throw RetestCommandException.unableToInspectWorkflowRuns(
                new IllegalStateException("Workflow run #" + workflowRun.getId() + " is missing workflow identity."));
    }

    private record AnalyzedWorkflowRun(GHWorkflowRun workflowRun, boolean completed, boolean hasFailedLatestJob) {
    }
}
