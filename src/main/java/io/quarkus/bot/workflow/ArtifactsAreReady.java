package io.quarkus.bot.workflow;

import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHWorkflowRun;

import java.util.List;
import java.util.concurrent.Callable;

public final class ArtifactsAreReady implements Callable<Boolean> {
    private final GHWorkflowRun workflowRun;
    private List<GHArtifact> artifacts;

    public ArtifactsAreReady(GHWorkflowRun workflowRun) {
        this.workflowRun = workflowRun;
    }

    @Override
    public Boolean call() throws Exception {
        artifacts = workflowRun.listArtifacts().toList();
        return !artifacts.isEmpty();
    }

    public List<GHArtifact> getArtifacts() {
        return artifacts;
    }
}
