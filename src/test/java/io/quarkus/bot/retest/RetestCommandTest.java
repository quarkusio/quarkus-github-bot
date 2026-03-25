package io.quarkus.bot.retest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;

import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;

class RetestCommandTest {

    @Test
    void shouldReportPartialFailureAfterStartingEarlierReruns() throws Exception {
        RetestCommand command = new RetestCommand();
        command.quarkusBotConfig = mock(QuarkusGitHubBotConfig.class);
        command.workflowRunSelector = mock(RetestWorkflowRunSelector.class);
        command.failedJobsRerunner = mock(FailedJobsRerunner.class);

        when(command.quarkusBotConfig.isDryRun()).thenReturn(false);

        QuarkusGitHubBotConfigFile quarkusBotConfigFile = configFileWithFeatureEnabled();

        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = RetestFixtures.repository("quarkusio/quarkus-github-bot");
        GHIssue issue = mock(GHIssue.class);
        GHPullRequest pullRequest = RetestFixtures.openPullRequest(repository);
        GHWorkflowRun firstWorkflowRun = RetestFixtures.failedCompletedRun(901L, 90L, "CI", 90L, 1L, repository);
        GHWorkflowRun secondWorkflowRun = RetestFixtures.failedCompletedRun(902L, 91L, "Native", 91L, 1L, repository);

        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(1);
        when(repository.getPullRequest(1)).thenReturn(pullRequest);
        when(command.workflowRunSelector.selectWorkflowRuns(pullRequest))
                .thenReturn(new RetestWorkflowSelection(List.of(firstWorkflowRun, secondWorkflowRun), null));

        doAnswer(invocation -> {
            GHWorkflowRun workflowRun = invocation.getArgument(1);
            if (workflowRun.getId() == 902L) {
                throw RetestCommandException.rerunFailedJobsFailed(902L, 500, null);
            }
            return null;
        }).when(command.failedJobsRerunner).rerunFailedJobs(eq(issueCommentPayload), any(GHWorkflowRun.class));

        assertThatThrownBy(() -> command.run(quarkusBotConfigFile, issueCommentPayload))
                .isInstanceOf(RetestCommandException.class)
                .hasMessageContaining("workflow runs #901")
                .hasMessageContaining("workflow run #902")
                .hasCauseInstanceOf(RetestCommandException.class);
    }

    private static QuarkusGitHubBotConfigFile configFileWithFeatureEnabled() throws Exception {
        QuarkusGitHubBotConfigFile quarkusBotConfigFile = new QuarkusGitHubBotConfigFile();
        Field featuresField = QuarkusGitHubBotConfigFile.class.getDeclaredField("features");
        featuresField.setAccessible(true);
        featuresField.set(quarkusBotConfigFile, new HashSet<>(Set.of(Feature.RETEST_PULL_REQUEST_WORKFLOWS)));
        return quarkusBotConfigFile;
    }
}
