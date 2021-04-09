package io.quarkus.bot.workflow;

import javax.enterprise.context.ApplicationScoped;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHPullRequest;

import io.quarkus.bot.workflow.report.WorkflowReport;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@ApplicationScoped
public class WorkflowReportFormatter {

    public String getCheckRunReportSummary(WorkflowReport report, GHPullRequest pullRequest) {
        return Templates.checkRunReportSummary(report, pullRequest).render();
    }

    public String getCheckRunReport(WorkflowReport report) {
        return Templates.checkRunReport(report).render();
    }

    public String getCommentReport(WorkflowReport report, GHCheckRun checkRun, String messageIdActive) {
        return Templates.commentReport(report, checkRun, messageIdActive).render();
    }

    @CheckedTemplate
    private static class Templates {

        public static native TemplateInstance checkRunReportSummary(WorkflowReport report, GHPullRequest pullRequest);

        public static native TemplateInstance checkRunReport(WorkflowReport report);

        public static native TemplateInstance commentReport(WorkflowReport report, GHCheckRun checkRun, String messageIdActive);
    }
}
