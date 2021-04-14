package io.quarkus.bot.workflow.report;

import java.util.List;
import java.util.stream.Collectors;

import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReport {

    private final String sha;
    private final List<WorkflowReportJob> jobs;
    private final boolean sameRepository;
    private final Conclusion conclusion;
    private final String workflowRunUrl;

    public WorkflowReport(String sha, List<WorkflowReportJob> jobs, boolean sameRepository, Conclusion conclusion,
            String workflowRunUrl) {
        this.sha = sha;
        this.jobs = jobs;
        this.sameRepository = sameRepository;
        this.conclusion = conclusion;
        this.workflowRunUrl = workflowRunUrl;
    }

    public String getSha() {
        return sha;
    }

    public void addJob(WorkflowReportJob job) {
        this.jobs.add(job);
    }

    public List<WorkflowReportJob> getJobs() {
        return jobs;
    }

    public List<WorkflowReportJob> getJobsWithTestFailures() {
        return jobs.stream().filter(j -> j.hasTestFailures()).collect(Collectors.toList());
    }

    public boolean hasJvmJobsFailing() {
        for (WorkflowReportJob job : jobs) {
            if (job.isFailing() && job.isJvm()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTestFailures() {
        for (WorkflowReportJob job : jobs) {
            if (job.hasTestFailures()) {
                return true;
            }
        }
        return false;
    }

    public boolean isSameRepository() {
        return sameRepository;
    }

    public boolean isCancelled() {
        return Conclusion.CANCELLED.equals(conclusion);
    }

    public boolean isFailure() {
        return Conclusion.FAILURE.equals(conclusion);
    }

    public String getWorkflowRunUrl() {
        return workflowRunUrl;
    }

    public boolean hasErrorDownloadingSurefireReports() {
        for (WorkflowReportJob job : jobs) {
            if (job.hasErrorDownloadingSurefireReports()) {
                return true;
            }
        }
        return false;
    }
}