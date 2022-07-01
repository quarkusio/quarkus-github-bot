package io.quarkus.bot.workflow.report;

public interface WorkflowReportJobIncludeStrategy {

    boolean include(WorkflowReport report, WorkflowReportJob job);
}
