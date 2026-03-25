package io.quarkus.bot.retest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;

import io.quarkiverse.githubapp.command.airline.CommandOptions;
import io.quarkiverse.githubapp.command.airline.ExecutionErrorHandler.ExecutionErrorContext;
import io.quarkiverse.githubapp.command.airline.runtime.AbstractCommandDispatcher.CommandExecutionContext;
import io.quarkiverse.githubapp.command.airline.runtime.CommandConfig;
import io.quarkus.bot.GHIssueCommentService;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.util.Strings;

class RetestExecutionErrorHandlerTest {

    @Test
    void shouldCommentKnownCommandErrors() throws Exception {
        RetestExecutionErrorHandler handler = newHandler(false);
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(repository.getFullName()).thenReturn("quarkusio/quarkus-github-bot");
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(123);

        handler.handleExecutionError(issueCommentPayload,
                new ExecutionErrorContext(commandExecutionContext("@quarkusbot retest"),
                        RetestCommandException
                                .noEligibleWorkflowRuns(RetestWorkflowSelection.NoEligibleReason.LATEST_RUNS_GREEN)));

        verify(issue).comment(Strings.commentByBot(RetestCommentFormatter.formatCommandMessage("@quarkusbot retest",
                ":white_check_mark: The latest workflow runs for this pull request are already green.")));
    }

    @Test
    void shouldCommentGenericMessageForUnexpectedErrors() throws Exception {
        RetestExecutionErrorHandler handler = newHandler(false);
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(repository.getFullName()).thenReturn("quarkusio/quarkus-github-bot");
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(123);

        handler.handleExecutionError(issueCommentPayload,
                new ExecutionErrorContext(commandExecutionContext("@quarkusbot retest"), new IllegalStateException("boom")));

        verify(issue).comment(Strings.commentByBot(RetestCommentFormatter.formatCommandMessage("@quarkusbot retest",
                ":rotating_light: An error occurred while executing the command.")));
    }

    @Test
    void shouldSwallowWhenCommentPostingFails() throws Exception {
        RetestExecutionErrorHandler handler = newHandler(false);
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(repository.getFullName()).thenReturn("quarkusio/quarkus-github-bot");
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(123);
        when(issue.comment(Strings.commentByBot(RetestCommentFormatter.formatCommandMessage("@quarkusbot retest",
                ":rotating_light: An error occurred while executing the command."))))
                .thenThrow(new IllegalStateException("comment failed"));

        handler.handleExecutionError(issueCommentPayload,
                new ExecutionErrorContext(commandExecutionContext("@quarkusbot retest"), new IllegalStateException("boom")));
    }

    @Test
    void shouldSkipPostingCommentsInDryRunMode() throws Exception {
        RetestExecutionErrorHandler handler = newHandler(true);
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(repository.getFullName()).thenReturn("quarkusio/quarkus-github-bot");
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(123);

        handler.handleExecutionError(issueCommentPayload,
                new ExecutionErrorContext(commandExecutionContext("@quarkusbot retest"), new IllegalStateException("boom")));

        verify(issue, never()).comment(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRespectExecutionErrorStrategyWhenMessagesAreDisabled() throws Exception {
        RetestExecutionErrorHandler handler = newHandler(false);
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(repository.getFullName()).thenReturn("quarkusio/quarkus-github-bot");
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(123);

        CommandConfig commandConfig = new CommandConfig(CommandOptions.CommandScope.PULL_REQUESTS,
                CommandOptions.ExecutionErrorStrategy.NONE, "", RetestExecutionErrorHandler.class,
                CommandOptions.ReactionStrategy.NONE);

        handler.handleExecutionError(issueCommentPayload,
                new ExecutionErrorContext(commandExecutionContext("@quarkusbot retest", commandConfig),
                        new IllegalStateException("boom")));

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
        return commandExecutionContext(commandLine, new CommandConfig(CommandOptions.CommandScope.PULL_REQUESTS,
                CommandOptions.ExecutionErrorStrategy.COMMENT_MESSAGE, "", RetestExecutionErrorHandler.class,
                CommandOptions.ReactionStrategy.NONE));
    }

    private static CommandExecutionContext<Object> commandExecutionContext(String commandLine, CommandConfig commandConfig) {
        return new CommandExecutionContext<>(commandLine, new Object(), commandConfig, null);
    }
}
