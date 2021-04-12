package io.quarkus.bot.workflow.report;

import org.apache.maven.plugins.surefire.report.ReportTestCase;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReportTestCase {

    private final String classPath;
    private final String fullName;
    private final String fullClassName;
    private final String failureType;
    private final String failureErrorLine;
    private final String abbreviatedFailureDetail;
    private final String failureDetail;
    private final String failureUrl;
    private final String shortenedFailureUrl;

    public WorkflowReportTestCase(String classPath, ReportTestCase reportTestCase, String abbreviatedFailureDetail,
            String failureUrl,
            String shortenedFailureUrl) {
        this.classPath = classPath;
        this.fullName = reportTestCase.getFullName();
        this.fullClassName = reportTestCase.getFullClassName();
        this.failureType = reportTestCase.getFailureType();
        this.failureErrorLine = reportTestCase.getFailureErrorLine();
        this.abbreviatedFailureDetail = abbreviatedFailureDetail;
        this.failureDetail = reportTestCase.getFailureDetail();
        this.failureUrl = failureUrl;
        this.shortenedFailureUrl = shortenedFailureUrl;
    }

    public String getClassPath() {
        return classPath;
    }

    public String getFullName() {
        return fullName;
    }

    public String getFullClassName() {
        return fullClassName;
    }

    public String getFailureType() {
        return failureType;
    }

    public String getFailureErrorLine() {
        return failureErrorLine;
    }

    public String getAbbreviatedFailureDetail() {
        return abbreviatedFailureDetail;
    }

    public String getFailureDetail() {
        return failureDetail;
    }

    public String getFailureUrl() {
        return failureUrl;
    }

    public String getShortenedFailureUrl() {
        return shortenedFailureUrl;
    }
}