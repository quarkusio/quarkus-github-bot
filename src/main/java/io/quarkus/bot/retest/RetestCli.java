package io.quarkus.bot.retest;

import org.kohsuke.github.GHEventPayload;

import com.github.rvesse.airline.annotations.Cli;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.command.airline.CliOptions;
import io.quarkiverse.githubapp.command.airline.CliOptions.ParseErrorStrategy;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;

/**
 * Command-airline entry point for comment commands handled by the bot.
 */
@Cli(name = "@quarkusbot", commands = RetestCommand.class)
@CliOptions(aliases = {
        "@quarkus-bot" }, parseErrorStrategy = ParseErrorStrategy.NONE)
class RetestCli {
}

/**
 * Shared command shape required by the command-airline processor.
 */
interface RetestCommandHandler {

    void run(@ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            GHEventPayload.IssueComment issueCommentPayload);
}
