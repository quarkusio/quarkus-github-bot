package io.quarkus.bot.buildreporter.githubactions.report;

public interface WorkflowReportJobIncludeStrategy {

    boolean include(WorkflowReport report, WorkflowReportJob job);
}
