package io.quarkus.bot.retest;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.kohsuke.github.GHEventPayload;

import io.quarkiverse.githubapp.command.airline.ExecutionErrorHandler;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.service.GHIssueCommentService;

/**
 * Formats command failures as pull-request comments.
 */
@Singleton
class RetestExecutionErrorHandler implements ExecutionErrorHandler {

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    @Inject
    GHIssueCommentService issueCommentService;

    @Override
    public void handleExecutionError(GHEventPayload.IssueComment issueCommentPayload,
            ExecutionErrorContext executionErrorContext) {
        if (!executionErrorContext.commandExecutionContext().getCommandConfig().getExecutionErrorStrategy()
                .addMessage()) {
            return;
        }

        String commandLine = executionErrorContext.commandExecutionContext().getCommandLine();
        String message = formatMessage(commandLine, executionErrorContext.exception());
        issueCommentService.addComment(issueCommentPayload.getIssue(), message, false, quarkusBotConfig.isDryRun());
    }

    private static String formatMessage(String commandLine, Exception exception) {
        if (exception instanceof RetestCommandException retestCommandException) {
            return retestCommandException.userMessage();
        }

        return ":rotating_light: An error occurred while executing `" + commandLine + "`.";
    }
}
