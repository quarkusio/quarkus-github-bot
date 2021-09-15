package io.quarkus.bot.test;

import java.util.List;

public class WorkflowResult {
    private String sha;
    private List<JobResult> jobs;

    public WorkflowResult() {
    }

    public WorkflowResult(List<JobResult> jobs, String sha) {
        this.jobs = jobs;
        this.sha = sha;
    }

    public List<JobResult> getJobs() {
        return jobs;
    }

    public String getSha() {
        return sha;
    }
}
