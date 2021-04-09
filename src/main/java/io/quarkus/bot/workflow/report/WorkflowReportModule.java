package io.quarkus.bot.workflow.report;

import java.util.List;

import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReportModule {

    private final String name;
    private final List<ReportTestSuite> reportTestSuites;
    private final List<WorkflowReportTestCase> failures;

    public WorkflowReportModule(String name, List<ReportTestSuite> reportTestSuites, List<WorkflowReportTestCase> failures) {
        this.name = name;
        this.reportTestSuites = reportTestSuites;
        this.failures = failures;
    }

    public String getName() {
        return name;
    }

    public boolean hasTestFailures() {
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            if (reportTestSuite.getNumberOfErrors() > 0 || reportTestSuite.getNumberOfFailures() > 0) {
                return true;
            }
        }
        return false;
    }

    public List<WorkflowReportTestCase> getFailures() {
        return failures;
    }

    public int getTestCount() {
        int testCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            testCount += reportTestSuite.getNumberOfTests();
        }
        return testCount;
    }

    public int getSuccessCount() {
        int successCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            for (ReportTestCase reportTestCase : reportTestSuite.getTestCases()) {
                if (reportTestCase.isSuccessful()) {
                    successCount++;
                }
            }
        }
        return successCount;
    }

    public int getErrorCount() {
        int testCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            testCount += reportTestSuite.getNumberOfErrors();
        }
        return testCount;
    }

    public int getFailureCount() {
        int testCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            testCount += reportTestSuite.getNumberOfFailures();
        }
        return testCount;
    }

    public int getSkippedCount() {
        int testCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            testCount += reportTestSuite.getNumberOfSkipped();
        }
        return testCount;
    }
}