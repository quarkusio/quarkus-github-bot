package io.quarkus.bot.workflow.report;

import java.util.List;

import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;

import io.quarkus.bot.build.reporting.model.BuildStatus;
import io.quarkus.bot.build.reporting.model.ProjectReport;
import io.quarkus.bot.workflow.StackTraceUtils;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReportModule {

    private final String name;
    private final ProjectReport projectReport;
    private final List<ReportTestSuite> reportTestSuites;
    private final List<WorkflowReportTestCase> failures;

    public WorkflowReportModule(String name, ProjectReport projectReport, List<ReportTestSuite> reportTestSuites,
            List<WorkflowReportTestCase> failures) {
        this.name = name;
        this.projectReport = projectReport;
        this.reportTestSuites = reportTestSuites;
        this.failures = failures;
    }

    public String getName() {
        return name;
    }

    public boolean hasReportedFailures() {
        return hasTestFailures() || hasBuildReportFailures();
    }

    public boolean hasTestFailures() {
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            if (reportTestSuite.getNumberOfErrors() > 0 || reportTestSuite.getNumberOfFailures() > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean hasBuildReportFailures() {
        return projectReport != null && projectReport.getStatus() == BuildStatus.FAILURE;
    }

    public List<WorkflowReportTestCase> getTestFailures() {
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

    public String getBuildReportFailure() {
        return projectReport != null ? StackTraceUtils.firstLines(projectReport.getError(), 5) : null;
    }
}