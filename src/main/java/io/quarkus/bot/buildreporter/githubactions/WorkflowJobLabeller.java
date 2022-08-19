package io.quarkus.bot.buildreporter.githubactions;

public interface WorkflowJobLabeller {

    String label(String name);
}
