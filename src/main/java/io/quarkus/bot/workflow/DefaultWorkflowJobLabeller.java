package io.quarkus.bot.workflow;

import javax.inject.Singleton;

import io.quarkus.arc.DefaultBean;

@Singleton
@DefaultBean
public class DefaultWorkflowJobLabeller implements WorkflowJobLabeller {

    @Override
    public String label(String name) {
        return name;
    }
}
