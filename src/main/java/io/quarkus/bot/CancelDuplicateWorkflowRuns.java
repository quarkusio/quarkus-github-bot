package io.quarkus.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Status;

import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.bot.config.QuarkusBotConfig;

public class CancelDuplicateWorkflowRuns {

    private static final Logger LOG = Logger.getLogger(CancelDuplicateWorkflowRuns.class);

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void cancelDuplicateWorkflowRuns(@WorkflowRun.Requested GHEventPayload.WorkflowRun workflowRunPayload) throws IOException {
        GHWorkflowRun workflowRun = workflowRunPayload.getWorkflowRun();

        if (!GHEvent.PUSH.equals(workflowRun.getEvent()) && !GHEvent.PULL_REQUEST.equals(workflowRun.getEvent())) {
            return;
        }

        List<GHWorkflowRun> workflowRunsToCancel = getWorkflowRunsToCancel(workflowRun);

        for (GHWorkflowRun workflowRunToCancel : workflowRunsToCancel) {
            try {
                if (!quarkusBotConfig.isDryRun()) {
                    workflowRunToCancel.cancel();
                } else {
                    LOG.info("Workflow run #" + workflowRun.getId() + " - Cancelling workflow run #"
                            + workflowRunToCancel.getId() + " for branch " + workflowRun.getHeadBranch());
                }
            } catch (Exception e) {
                LOG.error("Workflow run #" + workflowRun.getId() + " - Unable to cancel workflow run #"
                        + workflowRunToCancel.getId() + " for branch " + workflowRun.getHeadBranch());
            }
        }
    }

    private static List<GHWorkflowRun> getWorkflowRunsToCancel(GHWorkflowRun workflowRun) throws IOException {
        List<GHWorkflowRun> waitingWorkflowRuns = new ArrayList<>();
        waitingWorkflowRuns.addAll(workflowRun.getRepository()
                .queryWorkflowRuns()
                .branch(workflowRun.getHeadBranch())
                .status(Status.IN_PROGRESS)
                .list().toList());
        waitingWorkflowRuns.addAll(workflowRun.getRepository()
                .queryWorkflowRuns()
                .branch(workflowRun.getHeadBranch())
                .status(Status.QUEUED)
                .list().toList());

        return waitingWorkflowRuns.stream()
                .filter(wr -> wr.getWorkflowId() == workflowRun.getWorkflowId())
                .filter(wr -> wr.getId() < workflowRun.getId())
                .filter(wr -> GHEvent.PUSH.name().equals(getEvent(wr)) || GHEvent.PULL_REQUEST.name().equals(getEvent(wr)))
                .filter(wr -> wr.getHeadRepository().getId() == workflowRun.getHeadRepository().getId())
                .sorted((wr1, wr2) -> Long.compare(wr1.getId(), wr2.getId()))
                .collect(Collectors.toList());
    }

    private static String getEvent(GHWorkflowRun workflowRun) {
        try {
            return workflowRun.getEvent().name();
        } catch (Exception e) {
            return "_UNKNOWN_";
        }
    }
}
