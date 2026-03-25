package io.quarkus.bot.retest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPermissionType;

import com.github.rvesse.airline.help.Help;
import com.github.rvesse.airline.parser.errors.ParseException;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.GitHubConfigFileProvider;
import io.quarkiverse.githubapp.command.airline.ParseErrorHandler;
import io.quarkus.bot.GHIssueCommentService;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;

/**
 * Dry-run-aware parse error handler for retest commands.
 */
@Singleton
class RetestParseErrorHandler implements ParseErrorHandler {

    private static final Logger LOG = Logger.getLogger(RetestParseErrorHandler.class);
    private static final String CONFIG_FILE_PATH = "quarkus-github-bot.yml";

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    @Inject
    GHIssueCommentService issueCommentService;

    @Inject
    GitHubConfigFileProvider gitHubConfigFileProvider;

    @Override
    public void handleParseError(GHEventPayload.IssueComment issueCommentPayload, ParseErrorContext parseErrorContext) {
        if (!parseErrorContext.cliConfig().getParseErrorStrategy().addMessage()) {
            return;
        }

        boolean shouldComment;
        try {
            shouldComment = shouldCommentOnParseError(issueCommentPayload);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Unable to determine whether retest parse error should be reported in "
                    + location(issueCommentPayload), e);
            return;
        }
        if (!shouldComment) {
            return;
        }

        StringBuilder message = new StringBuilder(RetestCommentFormatter.formatCommandMessage(
                parseErrorContext.command(), ":rotating_light: Unable to parse the command."));

        if (parseErrorContext.cliConfig().getParseErrorStrategy().includeErrors()) {
            message.append("\n\nErrors:\n");
            if (parseErrorContext.error() != null) {
                message.append("\n- ").append(parseErrorContext.error());
            }
            if (parseErrorContext.parseResult() != null) {
                for (ParseException parseError : parseErrorContext.parseResult().getErrors()) {
                    message.append("\n- ").append(parseError.getMessage());
                }
            }
        }

        if (parseErrorContext.error() == null && parseErrorContext.cliConfig().getParseErrorStrategy().includeHelp()) {
            appendHelp(message, issueCommentPayload, parseErrorContext);
        }

        String location = location(issueCommentPayload);
        try {
            issueCommentService.addCommentOrThrow(issueCommentPayload.getIssue(), message.toString(), false,
                    quarkusBotConfig.isDryRun());
        } catch (Exception e) {
            LOG.warn("Error trying to add retest parse error comment for command in " + location, e);
        }
    }

    private boolean shouldCommentOnParseError(GHEventPayload.IssueComment issueCommentPayload) throws IOException {
        if (issueCommentPayload.getIssue() == null || !issueCommentPayload.getIssue().isPullRequest()) {
            return false;
        }
        if (issueCommentPayload.getRepository() == null) {
            return false;
        }
        if (issueCommentPayload.getSender() == null) {
            return false;
        }

        QuarkusGitHubBotConfigFile quarkusBotConfigFile = gitHubConfigFileProvider.fetchConfigFile(
                issueCommentPayload.getRepository(), CONFIG_FILE_PATH, ConfigFile.Source.DEFAULT,
                QuarkusGitHubBotConfigFile.class)
                .orElse(null);
        if (!Feature.RETEST_PULL_REQUEST_WORKFLOWS.isEnabled(quarkusBotConfigFile)) {
            return false;
        }

        return issueCommentPayload.getRepository().hasPermission(issueCommentPayload.getSender(), GHPermissionType.WRITE);
    }

    private void appendHelp(StringBuilder message, GHEventPayload.IssueComment issueCommentPayload,
            ParseErrorContext parseErrorContext) {
        try {
            ByteArrayOutputStream helpOs = new ByteArrayOutputStream();

            if (parseErrorContext.parseResult() != null
                    && parseErrorContext.parseResult().getState().getCommand() != null) {
                Help.help(parseErrorContext.parseResult().getState().getCommand(), helpOs);
            } else {
                Help.help(parseErrorContext.cli().getMetadata(), Collections.emptyList(), helpOs);
            }

            String help = helpOs.toString(StandardCharsets.UTF_8);
            if (!help.isBlank()) {
                message.append("\n\nHelp:\n\n```").append("\n").append(help.trim()).append("\n```");
            }
        } catch (IOException e) {
            LOG.warn("Error trying to generate help for retest parse error in " + location(issueCommentPayload), e);
        }
    }

    private static String location(GHEventPayload.IssueComment issueCommentPayload) {
        String repository = issueCommentPayload.getRepository() != null
                ? issueCommentPayload.getRepository().getFullName()
                : "unknown-repository";
        String issue = issueCommentPayload.getIssue() != null
                ? String.valueOf(issueCommentPayload.getIssue().getNumber())
                : "unknown-issue";
        return repository + "#" + issue;
    }
}
