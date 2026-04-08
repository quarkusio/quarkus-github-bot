package io.quarkus.bot.retest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import io.quarkiverse.githubapp.command.airline.CommandOptions;
import io.quarkiverse.githubapp.command.airline.ExecutionErrorHandler.ExecutionErrorContext;
import io.quarkiverse.githubapp.command.airline.runtime.AbstractCommandDispatcher.CommandExecutionContext;
import io.quarkiverse.githubapp.command.airline.runtime.CommandConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.service.GHIssueCommentService;
import io.quarkus.bot.util.Strings;

class RetestExecutionErrorHandlerTest {

    @Test
    void shouldCommentKnownCommandErrors() throws Exception {
        RetestExecutionErrorHandler handler = newHandler(false);
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHIssue issue = mock(GHIssue.class);
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(123);

        handler.handleExecutionError(issueCommentPayload,
                new ExecutionErrorContext(commandExecutionContext("@quarkusbot retest"),
                        RetestCommandException
                                .noEligibleWorkflowRuns(RetestWorkflowSelection.NoEligibleReason.LATEST_RUNS_GREEN)));

        verify(issue).comment(
                Strings.commentByBot(":white_check_mark: The latest workflow runs for this pull request are already green."));
    }

    @Test
    void shouldCommentGenericMessageForUnexpectedErrors() throws Exception {
        RetestExecutionErrorHandler handler = newHandler(false);
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHIssue issue = mock(GHIssue.class);
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(123);

        handler.handleExecutionError(issueCommentPayload,
                new ExecutionErrorContext(commandExecutionContext("@quarkusbot retest"), new IllegalStateException("boom")));

        verify(issue).comment(Strings.commentByBot(":rotating_light: An error occurred while executing `@quarkusbot retest`."));
    }

    @Test
    void shouldSkipPostingCommentsInDryRunMode() throws Exception {
        RetestExecutionErrorHandler handler = newHandler(true);
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHIssue issue = mock(GHIssue.class);
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(123);

        handler.handleExecutionError(issueCommentPayload,
                new ExecutionErrorContext(commandExecutionContext("@quarkusbot retest"), new IllegalStateException("boom")));

        verify(issue, never()).comment(org.mockito.ArgumentMatchers.anyString());
    }

    private static RetestExecutionErrorHandler newHandler(boolean dryRun) {
        RetestExecutionErrorHandler handler = new RetestExecutionErrorHandler();
        handler.issueCommentService = new GHIssueCommentService();
        handler.quarkusBotConfig = mock(QuarkusGitHubBotConfig.class);
        when(handler.quarkusBotConfig.isDryRun()).thenReturn(dryRun);
        return handler;
    }

    private static CommandExecutionContext<Object> commandExecutionContext(String commandLine) {
        return new CommandExecutionContext<>(commandLine, new Object(),
                new CommandConfig(CommandOptions.CommandScope.PULL_REQUESTS,
                        CommandOptions.ExecutionErrorStrategy.COMMENT_MESSAGE, "", RetestExecutionErrorHandler.class,
                        CommandOptions.ReactionStrategy.NONE),
                null);
    }
}
