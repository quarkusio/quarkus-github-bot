package io.quarkus.bot.buildreporter.report;

public interface WorkflowReportJobIncludeStrategy {

    boolean include(WorkflowReport report, WorkflowReportJob job);
}
