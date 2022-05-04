package io.quarkus.bot.workflow;

import javax.enterprise.context.ApplicationScoped;

import org.kohsuke.github.GHCheckRun;

import io.quarkus.bot.workflow.report.WorkflowReport;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@ApplicationScoped
public class WorkflowReportFormatter {

    public String getCheckRunReportSummary(WorkflowReport report, WorkflowContext workflowContext, boolean artifactsAvailable) {
        return Templates.checkRunReportSummary(report, workflowContext, artifactsAvailable).render();
    }

    public String getCheckRunReport(WorkflowReport report, boolean includeStackTraces) {
        return Templates.checkRunReport(report, includeStackTraces).render();
    }

    public String getReportComment(WorkflowReport report, boolean artifactsAvailable, GHCheckRun checkRun,
            String messageIdActive, boolean includeStackTraces) {
        return Templates.commentReport(report, artifactsAvailable, checkRun, messageIdActive, includeStackTraces).render();
    }

    @CheckedTemplate
    private static class Templates {

        public static native TemplateInstance checkRunReportSummary(WorkflowReport report, WorkflowContext workflowContext,
                boolean artifactsAvailable);

        public static native TemplateInstance checkRunReport(WorkflowReport report, boolean includeStackTraces);

        public static native TemplateInstance commentReport(WorkflowReport report, boolean artifactsAvailable,
                GHCheckRun checkRun, String messageIdActive, boolean includeStackTraces);
    }
}
