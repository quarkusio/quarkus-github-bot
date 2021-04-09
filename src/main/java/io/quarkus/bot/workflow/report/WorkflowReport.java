package io.quarkus.bot.workflow.report;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReport {

    private final String sha;
    private final List<WorkflowReportJob> jobs;
    private final boolean sameRepository;

    public WorkflowReport(String sha, List<WorkflowReportJob> jobs, boolean sameRepository) {
        this.sha = sha;
        this.jobs = jobs;
        this.sameRepository = sameRepository;
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

    public boolean hasErrorDownloadingSurefireReports() {
        for (WorkflowReportJob job : jobs) {
            if (job.hasErrorDownloadingSurefireReports()) {
                return true;
            }
        }
        return false;
    }
}