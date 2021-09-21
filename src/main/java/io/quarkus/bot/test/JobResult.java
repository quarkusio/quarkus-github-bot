package io.quarkus.bot.test;

import java.util.Date;
import java.util.List;

public class JobResult {
    private String jobUrl;
    private String jobName;
    private Date completedAt;

    private List<TestResult> tests;

    public JobResult(String jobUrl, String jobName, List<TestResult> tests, Date completedAt) {
        this.jobUrl = jobUrl;
        this.jobName = jobName;
        this.tests = tests;
        this.completedAt = completedAt;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public String getJobName() {
        return jobName;
    }

    public List<TestResult> getTests() {
        return tests;
    }

    public Date getCompletedAt() {
        return completedAt;
    }
}
