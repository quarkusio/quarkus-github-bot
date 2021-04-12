package io.quarkus.bot.workflow.report;

import java.util.List;

import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkus.bot.workflow.WorkflowConstants;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReportJob {

    private final String name;
    private final String testFailuresAnchor;
    private final Conclusion conclusion;
    private final String failingStep;
    private final String url;
    private final String rawLogsUrl;
    private final List<WorkflowReportModule> modules;
    private final boolean errorDownloadingSurefireReports;

    public WorkflowReportJob(String name, String testFailuresAnchor, Conclusion conclusion, String failingStep, String url,
            String rawLogsUrl, List<WorkflowReportModule> modules, boolean errorDownloadingSurefireReports) {
        this.name = name;
        this.testFailuresAnchor = testFailuresAnchor;
        this.conclusion = conclusion;
        this.failingStep = failingStep;
        this.url = url;
        this.rawLogsUrl = rawLogsUrl;
        this.modules = modules;
        this.errorDownloadingSurefireReports = errorDownloadingSurefireReports;
    }

    public String getName() {
        return name;
    }

    public String getTestFailuresAnchor() {
        return testFailuresAnchor;
    }

    public String getConclusionEmoji() {
        switch (conclusion) {
            case SUCCESS:
                return ":heavy_check_mark:";
            case FAILURE:
                return "✖";
            case CANCELLED:
                return ":hourglass:";
            default:
                return ":question:";
        }
    }

    public boolean isJvm() {
        return name.startsWith(WorkflowConstants.JVM_TESTS_PREFIX);
    }

    public boolean isFailing() {
        return !Conclusion.SUCCESS.equals(conclusion);
    }

    public String getFailingStep() {
        return failingStep;
    }

    public String getUrl() {
        return url;
    }

    public String getRawLogsUrl() {
        return rawLogsUrl;
    }

    public List<WorkflowReportModule> getModules() {
        return modules;
    }

    public boolean hasTestFailures() {
        for (WorkflowReportModule module : modules) {
            if (module.hasTestFailures()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasErrorDownloadingSurefireReports() {
        return errorDownloadingSurefireReports;
    }
}