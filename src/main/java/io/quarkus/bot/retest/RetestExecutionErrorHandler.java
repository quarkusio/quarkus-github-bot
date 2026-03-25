package io.quarkus.bot.retest;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;

import io.quarkiverse.githubapp.command.airline.ExecutionErrorHandler;
import io.quarkus.bot.GHIssueCommentService;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;

/**
 * Formats command failures as pull-request comments.
 */
@Singleton
class RetestExecutionErrorHandler implements ExecutionErrorHandler {

    private static final Logger LOG = Logger.getLogger(RetestExecutionErrorHandler.class);

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
        String location = issueCommentPayload.getRepository().getFullName() + "#" + issueCommentPayload.getIssue().getNumber();

        try {
            issueCommentService.addCommentOrThrow(issueCommentPayload.getIssue(), message, false, quarkusBotConfig.isDryRun());
        } catch (Exception e) {
            LOG.warn("Error trying to add retest execution error comment for command in " + location, e);
        }
    }

    static String formatMessage(String commandLine, Exception exception) {
        String userMessage = exception instanceof RetestCommandException retestCommandException
                ? retestCommandException.userMessage()
                : ":rotating_light: An error occurred while executing the command.";

        return RetestCommentFormatter.formatCommandMessage(commandLine, userMessage);
    }
}
