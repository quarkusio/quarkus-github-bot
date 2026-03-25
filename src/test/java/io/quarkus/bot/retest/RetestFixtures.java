package io.quarkus.bot.retest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.PagedIterable;

import io.quarkus.bot.it.MockHelper;

public final class RetestFixtures {

    public static final String DEFAULT_HEAD_SHA = "deadbeef";
    public static final String DEFAULT_HEAD_BRANCH = "feature/retest";

    private RetestFixtures() {
    }

    public static GHRepository repository(String fullName) {
        GHRepository repository = mock(GHRepository.class);
        String[] parts = fullName.split("/", 2);
        when(repository.getFullName()).thenReturn(fullName);
        when(repository.getOwnerName()).thenReturn(parts[0]);
        when(repository.getName()).thenReturn(parts[1]);
        return repository;
    }

    public static PullRequestFixture pullRequestFixture(GHRepository repository) {
        return new PullRequestFixture(repository);
    }

    public static GHPullRequest openPullRequest(GHRepository repository) {
        return pullRequestFixture(repository).build();
    }

    public static GHPullRequest openPullRequest(GHRepository repository, GHRepository headRepository) {
        return pullRequestFixture(repository).headRepository(headRepository).build();
    }

    public static GHPullRequest closedPullRequestWithSameHead(GHRepository repository, int number) {
        return pullRequestFixture(repository)
                .number(number)
                .state(GHIssueState.CLOSED)
                .build();
    }

    public static WorkflowRunFixture workflowRunFixture(long id, long workflowId, String name) {
        return new WorkflowRunFixture(id, workflowId, name);
    }

    public static GHWorkflowRun failedCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository headRepository) {
        return workflowRunFixture(id, workflowId, name)
                .runNumber(runNumber)
                .runAttempt(runAttempt)
                .headRepository(headRepository)
                .completed(GHWorkflowRun.Conclusion.FAILURE)
                .jobs(failedJob())
                .build();
    }

    public static GHWorkflowRun failedCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository headRepository, GHPullRequest associatedPullRequest) {
        return failedCompletedRun(id, workflowId, name, runNumber, runAttempt, headRepository,
                List.of(associatedPullRequest));
    }

    public static GHWorkflowRun failedCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository headRepository, List<GHPullRequest> associatedPullRequests) {
        return workflowRunFixture(id, workflowId, name)
                .runNumber(runNumber)
                .runAttempt(runAttempt)
                .headRepository(headRepository)
                .associatedPullRequests(associatedPullRequests)
                .completed(GHWorkflowRun.Conclusion.FAILURE)
                .jobs(failedJob())
                .build();
    }

    public static GHWorkflowRun failedCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository headRepository, GHEvent event) {
        return workflowRunFixture(id, workflowId, name)
                .runNumber(runNumber)
                .runAttempt(runAttempt)
                .headRepository(headRepository)
                .event(event)
                .completed(GHWorkflowRun.Conclusion.FAILURE)
                .jobs(failedJob())
                .build();
    }

    public static GHWorkflowRun failedCompletedRunOnHead(long id, long workflowId, String name, long runNumber,
            long runAttempt, String headSha, String headBranch, GHRepository headRepository) {
        return workflowRunFixture(id, workflowId, name)
                .runNumber(runNumber)
                .runAttempt(runAttempt)
                .headSha(headSha)
                .headBranch(headBranch)
                .headRepository(headRepository)
                .completed(GHWorkflowRun.Conclusion.FAILURE)
                .jobs(failedJob())
                .build();
    }

    public static GHWorkflowRun failedCompletedRunWithJobs(long id, long workflowId, String name, long runNumber,
            long runAttempt, GHRepository headRepository, AtomicInteger listJobsCalls, GHWorkflowJob... jobs) {
        return workflowRunFixture(id, workflowId, name)
                .runNumber(runNumber)
                .runAttempt(runAttempt)
                .headRepository(headRepository)
                .listJobsCalls(listJobsCalls)
                .completed(GHWorkflowRun.Conclusion.FAILURE)
                .jobs(jobs)
                .build();
    }

    public static GHWorkflowRun timedOutCompletedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository headRepository) {
        return workflowRunFixture(id, workflowId, name)
                .runNumber(runNumber)
                .runAttempt(runAttempt)
                .headRepository(headRepository)
                .completed(GHWorkflowRun.Conclusion.TIMED_OUT)
                .jobs(timedOutJob())
                .build();
    }

    public static GHWorkflowRun successfulCompletedRun(long id, long workflowId, String name, long runNumber,
            long runAttempt, GHRepository headRepository) {
        return workflowRunFixture(id, workflowId, name)
                .runNumber(runNumber)
                .runAttempt(runAttempt)
                .headRepository(headRepository)
                .completed(GHWorkflowRun.Conclusion.SUCCESS)
                .jobs(successfulJob())
                .build();
    }

    public static GHWorkflowRun successfulCompletedRun(long id, long workflowId, String name, long runNumber,
            long runAttempt, GHRepository headRepository, GHPullRequest associatedPullRequest) {
        return successfulCompletedRun(id, workflowId, name, runNumber, runAttempt, headRepository,
                List.of(associatedPullRequest));
    }

    public static GHWorkflowRun successfulCompletedRun(long id, long workflowId, String name, long runNumber,
            long runAttempt, GHRepository headRepository, List<GHPullRequest> associatedPullRequests) {
        return workflowRunFixture(id, workflowId, name)
                .runNumber(runNumber)
                .runAttempt(runAttempt)
                .headRepository(headRepository)
                .associatedPullRequests(associatedPullRequests)
                .completed(GHWorkflowRun.Conclusion.SUCCESS)
                .jobs(successfulJob())
                .build();
    }

    public static GHWorkflowRun queuedRun(long id, long workflowId, String name, long runNumber, long runAttempt,
            GHRepository headRepository) {
        return workflowRunFixture(id, workflowId, name)
                .runNumber(runNumber)
                .runAttempt(runAttempt)
                .headRepository(headRepository)
                .status(GHWorkflowRun.Status.QUEUED)
                .jobs(failedJob())
                .build();
    }

    public static GHWorkflowJob failedJob() {
        return job(GHWorkflowRun.Conclusion.FAILURE);
    }

    public static GHWorkflowJob timedOutJob() {
        return job(GHWorkflowRun.Conclusion.TIMED_OUT);
    }

    public static GHWorkflowJob startupFailureJob() {
        return job(GHWorkflowRun.Conclusion.STARTUP_FAILURE);
    }

    public static GHWorkflowJob successfulJob() {
        return job(GHWorkflowRun.Conclusion.SUCCESS);
    }

    private static GHWorkflowJob job(GHWorkflowRun.Conclusion conclusion) {
        GHWorkflowJob workflowJob = mock(GHWorkflowJob.class);
        when(workflowJob.getConclusion()).thenReturn(conclusion);
        return workflowJob;
    }

    private static URL workflowUrl(long workflowId) {
        if (workflowId <= 0) {
            return null;
        }

        try {
            return new URL("https://api.github.com/workflows/" + workflowId);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static final class PullRequestFixture {

        private final GHRepository repository;
        private int number = 1;
        private GHIssueState state = GHIssueState.OPEN;
        private GHRepository headRepository;
        private String headRef = DEFAULT_HEAD_BRANCH;
        private String headSha = DEFAULT_HEAD_SHA;

        private PullRequestFixture(GHRepository repository) {
            this.repository = repository;
            this.headRepository = repository;
        }

        public PullRequestFixture number(int number) {
            this.number = number;
            return this;
        }

        public PullRequestFixture state(GHIssueState state) {
            this.state = state;
            return this;
        }

        public PullRequestFixture headRepository(GHRepository headRepository) {
            this.headRepository = headRepository;
            return this;
        }

        public PullRequestFixture headRef(String headRef) {
            this.headRef = headRef;
            return this;
        }

        public PullRequestFixture headSha(String headSha) {
            this.headSha = headSha;
            return this;
        }

        public GHPullRequest build() {
            GHPullRequest pullRequest = mock(GHPullRequest.class);
            GHCommitPointer head = mock(GHCommitPointer.class);
            when(pullRequest.getNumber()).thenReturn(number);
            when(pullRequest.getState()).thenReturn(state);
            when(pullRequest.getRepository()).thenReturn(repository);
            when(pullRequest.getHead()).thenReturn(head);
            when(head.getRef()).thenReturn(headRef);
            when(head.getSha()).thenReturn(headSha);
            when(head.getRepository()).thenReturn(headRepository);
            return pullRequest;
        }
    }

    public static final class WorkflowRunFixture {

        private final long id;
        private final long workflowId;
        private final String name;
        private long runNumber;
        private long runAttempt = 1;
        private URL workflowUrl;
        private String headSha = DEFAULT_HEAD_SHA;
        private String headBranch = DEFAULT_HEAD_BRANCH;
        private GHRepository repository;
        private GHRepository headRepository;
        private GHWorkflowRun.Status status = GHWorkflowRun.Status.COMPLETED;
        private GHWorkflowRun.Conclusion conclusion;
        private GHEvent event = GHEvent.PULL_REQUEST;
        private List<GHPullRequest> associatedPullRequests = List.of();
        private AtomicInteger listJobsCalls;
        private GHWorkflowJob[] jobs = new GHWorkflowJob[0];

        private WorkflowRunFixture(long id, long workflowId, String name) {
            this.id = id;
            this.workflowId = workflowId;
            this.name = name;
            this.runNumber = workflowId;
            this.workflowUrl = RetestFixtures.workflowUrl(workflowId);
        }

        public WorkflowRunFixture runNumber(long runNumber) {
            this.runNumber = runNumber;
            return this;
        }

        public WorkflowRunFixture runAttempt(long runAttempt) {
            this.runAttempt = runAttempt;
            return this;
        }

        public WorkflowRunFixture workflowUrl(URL workflowUrl) {
            this.workflowUrl = workflowUrl;
            return this;
        }

        public WorkflowRunFixture headSha(String headSha) {
            this.headSha = headSha;
            return this;
        }

        public WorkflowRunFixture headBranch(String headBranch) {
            this.headBranch = headBranch;
            return this;
        }

        public WorkflowRunFixture repository(GHRepository repository) {
            this.repository = repository;
            return this;
        }

        public WorkflowRunFixture headRepository(GHRepository headRepository) {
            this.headRepository = headRepository;
            if (this.repository == null) {
                this.repository = headRepository;
            }
            return this;
        }

        public WorkflowRunFixture status(GHWorkflowRun.Status status) {
            this.status = status;
            return this;
        }

        public WorkflowRunFixture completed(GHWorkflowRun.Conclusion conclusion) {
            this.status = GHWorkflowRun.Status.COMPLETED;
            this.conclusion = conclusion;
            return this;
        }

        public WorkflowRunFixture event(GHEvent event) {
            this.event = event;
            return this;
        }

        public WorkflowRunFixture associatedPullRequests(List<GHPullRequest> associatedPullRequests) {
            this.associatedPullRequests = associatedPullRequests;
            return this;
        }

        public WorkflowRunFixture listJobsCalls(AtomicInteger listJobsCalls) {
            this.listJobsCalls = listJobsCalls;
            return this;
        }

        public WorkflowRunFixture jobs(GHWorkflowJob... jobs) {
            this.jobs = jobs;
            return this;
        }

        public GHWorkflowRun build() {
            GHRepository runRepository = repository;
            GHRepository runHeadRepository = headRepository;
            GHWorkflowRun.Status runStatus = status;
            GHWorkflowRun.Conclusion runConclusion = conclusion;
            GHEvent runEvent = event;
            List<GHPullRequest> runAssociatedPullRequests = associatedPullRequests;
            AtomicInteger runListJobsCalls = listJobsCalls;
            GHWorkflowJob[] runJobs = jobs;
            URL runWorkflowUrl = workflowUrl;
            String runHeadSha = headSha;
            String runHeadBranch = headBranch;
            long runNumberValue = runNumber;
            long runAttemptValue = runAttempt;

            return new GHWorkflowRun() {
                @Override
                public long getId() {
                    return id;
                }

                @Override
                public String getName() {
                    return name;
                }

                @Override
                public long getRunNumber() {
                    return runNumberValue;
                }

                @Override
                public long getWorkflowId() {
                    return workflowId;
                }

                @Override
                public long getRunAttempt() {
                    return runAttemptValue;
                }

                @Override
                public URL getWorkflowUrl() {
                    return runWorkflowUrl;
                }

                @Override
                public String getHeadSha() {
                    return runHeadSha;
                }

                @Override
                public String getHeadBranch() {
                    return runHeadBranch;
                }

                @Override
                public GHRepository getRepository() {
                    return runRepository;
                }

                @Override
                public GHRepository getHeadRepository() {
                    return runHeadRepository;
                }

                @Override
                public GHWorkflowRun.Status getStatus() {
                    return runStatus;
                }

                @Override
                public GHWorkflowRun.Conclusion getConclusion() {
                    return runConclusion;
                }

                @Override
                public GHEvent getEvent() {
                    return runEvent;
                }

                @Override
                public List<GHPullRequest> getPullRequests() {
                    return runAssociatedPullRequests;
                }

                @Override
                public PagedIterable<GHWorkflowJob> listJobs() {
                    if (runListJobsCalls != null) {
                        runListJobsCalls.incrementAndGet();
                    }
                    return MockHelper.mockPagedIterable(runJobs);
                }

                @Override
                public URL getHtmlUrl() {
                    return null;
                }
            };
        }
    }
}
