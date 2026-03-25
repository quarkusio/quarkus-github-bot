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
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.PagedIterable;

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
        List<GHPullRequest> matchingPullRequests = matchingPullRequestsForHead(pullRequest, pullRequestHead,
                pullRequestHeadRepository);

        ensureUniquePullRequestForHead(matchingPullRequests);

        Map<String, GHWorkflowRun> latestRunsByWorkflow = new LinkedHashMap<>();
        collectWorkflowRuns(latestRunsByWorkflow, pullRequest, pullRequestHead, pullRequestHeadRepository,
                "pull_request");
        collectWorkflowRuns(latestRunsByWorkflow, pullRequest, pullRequestHead, pullRequestHeadRepository,
                PULL_REQUEST_TARGET_EVENT);

        List<GHWorkflowRun> latestRuns = new ArrayList<>(latestRunsByWorkflow.values());
        List<AnalyzedWorkflowRun> analyzedRuns = analyzeRuns(latestRuns);
        List<GHWorkflowRun> eligibleRuns = eligibleRuns(analyzedRuns);

        eligibleRuns.sort(Comparator.comparingLong(GHWorkflowRun::getId).reversed());
        return new RetestWorkflowSelection(eligibleRuns, noEligibleReason(analyzedRuns, eligibleRuns));
    }

    private static GHWorkflowRun latestRun(GHWorkflowRun left, GHWorkflowRun right) {
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

    private static List<AnalyzedWorkflowRun> analyzeRuns(List<GHWorkflowRun> latestRuns) throws IOException {
        List<AnalyzedWorkflowRun> analyzedRuns = new ArrayList<>(latestRuns.size());
        for (GHWorkflowRun workflowRun : latestRuns) {
            analyzedRuns.add(analyzeRun(workflowRun));
        }
        return analyzedRuns;
    }

    private static AnalyzedWorkflowRun analyzeRun(GHWorkflowRun workflowRun) throws IOException {
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

    private static boolean hasFailedLatestJob(GHWorkflowRun workflowRun) throws IOException {
        for (GHWorkflowJob workflowJob : workflowRun.listJobs()) {
            if (FAILED_JOB_CONCLUSIONS.contains(workflowJob.getConclusion())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSameRepository(GHRepository left, GHRepository right) {
        if (left == null || right == null) {
            return false;
        }

        String leftFullName = left.getFullName();
        String rightFullName = right.getFullName();
        if (leftFullName == null || rightFullName == null) {
            return false;
        }

        return leftFullName.equals(rightFullName);
    }

    private static void collectWorkflowRuns(Map<String, GHWorkflowRun> latestRunsByWorkflow,
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
            if (!pullRequestHead.getSha().equals(workflowRun.getHeadSha())) {
                continue;
            }
            if (!pullRequestHead.getRef().equals(workflowRun.getHeadBranch())) {
                continue;
            }
            if (!isSameRepository(pullRequestHeadRepository, workflowRun.getHeadRepository())) {
                continue;
            }

            List<GHPullRequest> associatedPullRequests = workflowRun.getPullRequests();
            if (associatedPullRequests != null && !associatedPullRequests.isEmpty()) {
                boolean matchesCurrentPullRequest = associatedPullRequests.stream()
                        .anyMatch(associatedPullRequest -> associatedPullRequest.getNumber() == pullRequest.getNumber());
                if (!matchesCurrentPullRequest) {
                    continue;
                }
            }

            latestRunsByWorkflow.merge(workflowIdentity(workflowRun, event), workflowRun,
                    RetestWorkflowRunSelector::latestRun);
        }
    }

    private static void ensureUniquePullRequestForHead(List<GHPullRequest> matchingPullRequests) {
        if (matchingPullRequests.size() > 1) {
            throw RetestCommandException.ambiguousPullRequestHead();
        }
    }

    private static List<GHPullRequest> matchingPullRequestsForHead(GHPullRequest pullRequest,
            GHCommitPointer pullRequestHead,
            GHRepository pullRequestHeadRepository) throws IOException {
        if (pullRequestHeadRepository == null) {
            return List.of(pullRequest);
        }

        String fullyQualifiedBranchName = pullRequestHeadRepository.getOwnerName() + ":" + pullRequestHead.getRef();
        List<GHPullRequest> matchingPullRequests = new ArrayList<>();

        for (GHPullRequest candidatePullRequest : pullRequest.getRepository().queryPullRequests()
                .state(GHIssueState.ALL)
                .head(fullyQualifiedBranchName)
                .list()) {
            if (!pullRequestHead.getRef().equals(candidatePullRequest.getHead().getRef())) {
                continue;
            }
            if (!pullRequestHead.getSha().equals(candidatePullRequest.getHead().getSha())) {
                continue;
            }
            if (!isSameRepository(pullRequestHeadRepository, candidatePullRequest.getHead().getRepository())) {
                continue;
            }

            matchingPullRequests.add(candidatePullRequest);
        }

        return matchingPullRequests;
    }

    private static String workflowIdentity(GHWorkflowRun workflowRun, String event) {
        if (workflowRun.getWorkflowId() > 0) {
            return "workflow-id:" + workflowRun.getWorkflowId() + ":event:" + event;
        }

        URL workflowUrl = workflowRun.getWorkflowUrl();
        if (workflowUrl != null) {
            return "workflow-url:" + workflowUrl + ":event:" + event;
        }

        return event + "::" + String.valueOf(workflowRun.getName()) + "::" + workflowRun.getRunNumber();
    }

    private record AnalyzedWorkflowRun(GHWorkflowRun workflowRun, boolean completed, boolean hasFailedLatestJob) {
    }
}
