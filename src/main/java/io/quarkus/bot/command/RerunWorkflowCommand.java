package io.quarkus.bot.command;

import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.workflow.WorkflowConstants;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.ReactionContent;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class RerunWorkflowCommand implements Command<GHPullRequest> {

    private static final Logger LOG = Logger.getLogger(RerunWorkflowCommand.class);

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    @Override
    public List<String> labels() {
        return Arrays.asList("test", "retest");
    }

    @Override
    public ReactionContent run(GHPullRequest pullRequest) throws IOException {
        GHRepository repository = pullRequest.getRepository();

        List<GHWorkflowRun> ghWorkflowRuns = repository
                .queryWorkflowRuns()
                .branch(pullRequest.getHead().getRef())
                .status(GHWorkflowRun.Status.COMPLETED)
                .list().toList();

        Map<String, Optional<GHWorkflowRun>> lastWorkflowRuns = ghWorkflowRuns.stream()
                .filter(workflowRun -> WorkflowConstants.QUARKUS_CI_WORKFLOW_NAME.equals(workflowRun.getName())
                        || WorkflowConstants.QUARKUS_DOCUMENTATION_CI_WORKFLOW_NAME.equals(workflowRun.getName()))
                .filter(workflowRun -> workflowRun.getHeadRepository().getOwnerName()
                        .equals(pullRequest.getHead().getRepository().getOwnerName()))
                .collect(Collectors.groupingBy(GHWorkflowRun::getName,
                        Collectors.maxBy(Comparator.comparing(GHWorkflowRun::getRunNumber))));

        for (Map.Entry<String, Optional<GHWorkflowRun>> lastWorkflowRun : lastWorkflowRuns.entrySet()) {
            if (lastWorkflowRun.getValue().isPresent()) {
                if (!quarkusBotConfig.isDryRun()) {
                    lastWorkflowRun.getValue().get().rerun();
                    LOG.debug("Pull request #" + pullRequest.getNumber() + " - Restart workflow: "
                            + lastWorkflowRun.getValue().get().getHtmlUrl());
                } else {
                    LOG.info("Pull request #" + pullRequest.getNumber() + " - Restart workflow " + lastWorkflowRun.getKey());
                }
            }
        }
        return ReactionContent.ROCKET;
    }
}
