package io.quarkus.bot.command;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.ReactionContent;
import org.kohsuke.github.extras.okhttp3.OkHttpConnector;

import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.workflow.WorkflowConstants;
import okhttp3.OkHttpClient;

@ApplicationScoped
public class RerunWorkflowCommand implements Command<GHPullRequest> {

    private static final Logger LOG = Logger.getLogger(RerunWorkflowCommand.class);

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    @Inject
    OkHttpClient okHttpClient;

    private GitHub gitHub;

    @PostConstruct
    public void initGitHubClient() throws IOException {
        if (quarkusBotConfig.getAccessToken().isPresent()) {
            gitHub = new GitHubBuilder().withOAuthToken(quarkusBotConfig.getAccessToken().get())
                    .withConnector(new OkHttpConnector(okHttpClient)).build();
        }
    }

    @Override
    public List<String> labels() {
        return Arrays.asList("test", "retest");
    }

    @Override
    public ReactionContent run(GHPullRequest pullRequest) throws IOException {
        if (gitHub == null) {
            LOG.error("Pull request #" + pullRequest.getNumber()
                    + " - Unable to restart workflow as no access token was provided in the config");
            return ReactionContent.MINUS_ONE;
        }

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

        boolean workflowRunRestarted = false;

        for (Map.Entry<String, Optional<GHWorkflowRun>> lastWorkflowRunEntry : lastWorkflowRuns.entrySet()) {
            if (lastWorkflowRunEntry.getValue().isPresent()) {
                GHWorkflowRun lastWorkflowRun = lastWorkflowRunEntry.getValue().get();

                // There is a bug in the GitHub API and we have to use a personal access token to execute the rerun() call
                GHRepository accessTokenRepository = gitHub.getRepository(lastWorkflowRun.getRepository().getFullName());
                GHWorkflowRun accessTokenLastWorkflowRun = accessTokenRepository.getWorkflowRun(lastWorkflowRun.getId());

                if (!quarkusBotConfig.isDryRun()) {
                    accessTokenLastWorkflowRun.rerun();
                    workflowRunRestarted = true;
                    LOG.debug("Pull request #" + pullRequest.getNumber() + " - Restart workflow: "
                            + lastWorkflowRun.getName() + " - " + lastWorkflowRun.getId());
                } else {
                    LOG.info("Pull request #" + pullRequest.getNumber() + " - Restart workflow "
                            + lastWorkflowRun.getName() + " - " + lastWorkflowRun.getId());
                }
            }
        }

        return workflowRunRestarted ? ReactionContent.ROCKET : ReactionContent.CONFUSED;
    }
}
