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
        PullRequestHeadContext headContext = pullRequestHeadContext(pullRequest);
        Map<String, WorkflowCandidates> workflowCandidates = collectWorkflowCandidates(pullRequest, headContext);
        List<GHWorkflowRun> latestRuns = resolveLatestRuns(workflowCandidates);
        List<AnalyzedWorkflowRun> analyzedRuns = analyzeRuns(latestRuns);
        List<GHWorkflowRun> eligibleRuns = eligibleRuns(analyzedRuns);

        eligibleRuns.sort(Comparator.comparingLong(GHWorkflowRun::getId).reversed());
        return new RetestWorkflowSelection(eligibleRuns, noEligibleReason(analyzedRuns, eligibleRuns));
    }

    private static PullRequestHeadContext pullRequestHeadContext(GHPullRequest pullRequest) throws IOException {
        GHCommitPointer pullRequestHead = pullRequest.getHead();
        GHRepository pullRequestHeadRepository = pullRequestHead.getRepository();
        boolean ambiguousPullRequestHead = matchingPullRequestsForHead(pullRequest, pullRequestHead,
                pullRequestHeadRepository).size() > 1;
        return new PullRequestHeadContext(pullRequestHead, pullRequestHeadRepository, ambiguousPullRequestHead);
    }

    private static Map<String, WorkflowCandidates> collectWorkflowCandidates(GHPullRequest pullRequest,
            PullRequestHeadContext headContext) throws IOException {
        Map<String, WorkflowCandidates> workflowCandidates = new LinkedHashMap<>();
        collectWorkflowCandidates(workflowCandidates, pullRequest, headContext, "pull_request");
        collectWorkflowCandidates(workflowCandidates, pullRequest, headContext, PULL_REQUEST_TARGET_EVENT);
        return workflowCandidates;
    }

    private static void collectWorkflowCandidates(Map<String, WorkflowCandidates> workflowCandidates,
            GHPullRequest pullRequest,
            PullRequestHeadContext headContext,
            String event) throws IOException {
        PagedIterable<GHWorkflowRun> workflowRuns = pullRequest.getRepository().queryWorkflowRuns()
                .headSha(headContext.pullRequestHead().getSha())
                .event(event)
                .list()
                .withPageSize(WORKFLOW_RUN_PAGE_SIZE);

        for (GHWorkflowRun workflowRun : workflowRuns) {
            if (!matchesPullRequestHead(workflowRun, headContext)) {
                continue;
            }

            String workflowIdentity = workflowIdentity(workflowRun, event);
            WorkflowCandidates candidates = workflowCandidates.getOrDefault(workflowIdentity, WorkflowCandidates.EMPTY);
            List<GHPullRequest> associatedPullRequests = workflowRun.getPullRequests();

            if (isAssociatedWithCurrentPullRequest(associatedPullRequests, pullRequest)) {
                workflowCandidates.put(workflowIdentity, candidates.withAcceptedRun(workflowRun));
                continue;
            }
            if (associatedPullRequests != null && !associatedPullRequests.isEmpty()) {
                continue;
            }
            if (headContext.ambiguousPullRequestHead()) {
                workflowCandidates.put(workflowIdentity, candidates.withAmbiguousUnassociatedRun(workflowRun));
            } else {
                workflowCandidates.put(workflowIdentity, candidates.withAcceptedRun(workflowRun));
            }
        }
    }

    private static List<GHWorkflowRun> resolveLatestRuns(Map<String, WorkflowCandidates> workflowCandidates) {
        List<GHWorkflowRun> latestRuns = new ArrayList<>(workflowCandidates.size());
        for (WorkflowCandidates candidates : workflowCandidates.values()) {
            latestRuns.add(resolveLatestRun(candidates));
        }
        return latestRuns;
    }

    private static GHWorkflowRun resolveLatestRun(WorkflowCandidates candidates) {
        GHWorkflowRun ambiguousUnassociatedRun = candidates.ambiguousUnassociatedRun();
        if (ambiguousUnassociatedRun == null) {
            return candidates.acceptedRun();
        }

        GHWorkflowRun acceptedRun = candidates.acceptedRun();
        if (acceptedRun == null) {
            throw RetestCommandException.ambiguousPullRequestHead();
        }
        if (latestWorkflowRun(acceptedRun, ambiguousUnassociatedRun) != acceptedRun) {
            throw RetestCommandException.ambiguousPullRequestHead();
        }

        return acceptedRun;
    }

    private static boolean matchesPullRequestHead(GHWorkflowRun workflowRun, PullRequestHeadContext headContext) {
        if (!headContext.pullRequestHead().getSha().equals(workflowRun.getHeadSha())) {
            return false;
        }
        if (!headContext.pullRequestHead().getRef().equals(workflowRun.getHeadBranch())) {
            return false;
        }
        return isSameRepository(headContext.pullRequestHeadRepository(), workflowRun.getHeadRepository());
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

        return event + "::" + workflowRun.getName() + "::" + workflowRun.getRunNumber();
    }

    private record PullRequestHeadContext(GHCommitPointer pullRequestHead, GHRepository pullRequestHeadRepository,
            boolean ambiguousPullRequestHead) {
    }

    private record WorkflowCandidates(GHWorkflowRun acceptedRun, GHWorkflowRun ambiguousUnassociatedRun) {
        private static final WorkflowCandidates EMPTY = new WorkflowCandidates(null, null);

        private WorkflowCandidates withAcceptedRun(GHWorkflowRun workflowRun) {
            return new WorkflowCandidates(latestWorkflowRun(acceptedRun, workflowRun), ambiguousUnassociatedRun);
        }

        private WorkflowCandidates withAmbiguousUnassociatedRun(GHWorkflowRun workflowRun) {
            return new WorkflowCandidates(acceptedRun, latestWorkflowRun(ambiguousUnassociatedRun, workflowRun));
        }
    }

    private record AnalyzedWorkflowRun(GHWorkflowRun workflowRun, boolean completed, boolean hasFailedLatestJob) {
    }
}
