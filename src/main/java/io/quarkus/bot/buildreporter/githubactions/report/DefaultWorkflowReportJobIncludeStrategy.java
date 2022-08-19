package io.quarkus.bot.buildreporter.githubactions.report;

import javax.inject.Singleton;

import io.quarkus.arc.DefaultBean;

@Singleton
@DefaultBean
public class DefaultWorkflowReportJobIncludeStrategy implements WorkflowReportJobIncludeStrategy {

    @Override
    public boolean include(WorkflowReport report, WorkflowReportJob job) {
        return true;
    }
}
