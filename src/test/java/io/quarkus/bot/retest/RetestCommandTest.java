package io.quarkus.bot.retest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;

import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.service.GHIssueCommentService;

class RetestCommandTest {

    @Test
    void shouldRejectWhenFeatureIsDisabled() {
        RetestCommand command = newCommand(false);

        assertThatThrownBy(() -> command.run(new QuarkusGitHubBotConfigFile(), mock(GHEventPayload.IssueComment.class)))
                .isInstanceOf(RetestCommandException.class)
                .hasMessageContaining("workflow retest is disabled");
    }

    @Test
    void shouldRejectClosedPullRequests() throws Exception {
        RetestCommand command = newCommand(false);
        QuarkusGitHubBotConfigFile quarkusBotConfigFile = RetestFixtures.configFileWithRetestFeatureEnabled();
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        GHPullRequest pullRequest = mock(GHPullRequest.class);

        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(1);
        when(repository.getPullRequest(1)).thenReturn(pullRequest);
        when(pullRequest.getState()).thenReturn(GHIssueState.CLOSED);

        assertThatThrownBy(() -> command.run(quarkusBotConfigFile, issueCommentPayload))
                .isInstanceOf(RetestCommandException.class)
                .hasMessageContaining("only available on open pull requests");
    }

    @Test
    void shouldSkipRerunsAndCommentsInDryRun() throws Exception {
        RetestCommand command = newCommand(true);
        QuarkusGitHubBotConfigFile quarkusBotConfigFile = RetestFixtures.configFileWithRetestFeatureEnabled();
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        GHPullRequest pullRequest = mock(GHPullRequest.class);
        GHWorkflowRun workflowRun = workflowRun(901L);

        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(1);
        when(repository.getPullRequest(1)).thenReturn(pullRequest);
        when(pullRequest.getNumber()).thenReturn(1);
        when(pullRequest.getState()).thenReturn(GHIssueState.OPEN);
        when(command.workflowRunSelector.selectWorkflowRuns(pullRequest))
                .thenReturn(new RetestWorkflowSelection(List.of(workflowRun), null));

        command.run(quarkusBotConfigFile, issueCommentPayload);

        verify(command.failedJobsRerunner, never()).rerunFailedJobs(any(GHEventPayload.IssueComment.class),
                any(GHWorkflowRun.class));
        verify(command.issueCommentService, never()).addComment(any(GHIssue.class), anyString(), anyBoolean(),
                anyBoolean());
    }

    @Test
    void shouldReportPartialFailureAfterStartingEarlierReruns() throws Exception {
        RetestCommand command = newCommand(false);
        QuarkusGitHubBotConfigFile quarkusBotConfigFile = RetestFixtures.configFileWithRetestFeatureEnabled();
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        GHPullRequest pullRequest = mock(GHPullRequest.class);
        GHWorkflowRun firstWorkflowRun = workflowRun(901L);
        GHWorkflowRun secondWorkflowRun = workflowRun(902L);

        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(1);
        when(repository.getPullRequest(1)).thenReturn(pullRequest);
        when(pullRequest.getState()).thenReturn(GHIssueState.OPEN);
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

    private static RetestCommand newCommand(boolean dryRun) {
        RetestCommand command = new RetestCommand();
        command.quarkusBotConfig = mock(QuarkusGitHubBotConfig.class);
        command.workflowRunSelector = mock(RetestWorkflowRunSelector.class);
        command.failedJobsRerunner = mock(FailedJobsRerunner.class);
        command.issueCommentService = mock(GHIssueCommentService.class);
        when(command.quarkusBotConfig.isDryRun()).thenReturn(dryRun);
        return command;
    }

    private static GHWorkflowRun workflowRun(long id) {
        return new GHWorkflowRun() {
            @Override
            public long getId() {
                return id;
            }

            @Override
            public URL getHtmlUrl() {
                return null;
            }
        };
    }
}
