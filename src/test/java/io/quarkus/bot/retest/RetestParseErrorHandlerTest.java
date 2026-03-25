package io.quarkus.bot.retest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.GitHubConfigFileProvider;
import io.quarkiverse.githubapp.command.airline.CliOptions.ParseErrorStrategy;
import io.quarkiverse.githubapp.command.airline.ParseErrorHandler.ParseErrorContext;
import io.quarkiverse.githubapp.command.airline.runtime.CliConfig;
import io.quarkiverse.githubapp.command.airline.runtime.CommandConfig;
import io.quarkiverse.githubapp.command.airline.runtime.CommandPermissionConfig;
import io.quarkiverse.githubapp.command.airline.runtime.CommandTeamConfig;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;

class RetestParseErrorHandlerTest {

    @Test
    void shouldIgnoreParseErrorWhenGatingCannotBeEvaluated() throws Exception {
        RetestParseErrorHandler handler = new RetestParseErrorHandler();
        handler.gitHubConfigFileProvider = mock(GitHubConfigFileProvider.class);

        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        GHUser sender = mock(GHUser.class);
        CliConfig cliConfig = new CliConfig(List.of("@quarkusbot"), ParseErrorStrategy.COMMENT_MESSAGE, "",
                RetestParseErrorHandler.class, new CommandConfig(), new CommandPermissionConfig(null),
                new CommandTeamConfig(null));

        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(issueCommentPayload.getIssue()).thenReturn(issue);
        when(issueCommentPayload.getSender()).thenReturn(sender);
        when(repository.getFullName()).thenReturn("quarkusio/quarkus-github-bot");
        when(issue.getNumber()).thenReturn(123);
        when(issue.isPullRequest()).thenReturn(true);
        when(handler.gitHubConfigFileProvider.fetchConfigFile(repository, "quarkus-github-bot.yml",
                ConfigFile.Source.DEFAULT, QuarkusGitHubBotConfigFile.class))
                .thenReturn(java.util.Optional.of(configFileWithFeatureEnabled()));
        when(repository.hasPermission(sender, GHPermissionType.WRITE)).thenThrow(new IOException("boom"));

        ParseErrorContext parseErrorContext = new ParseErrorContext(cliConfig, null, "@quarkusbot \"retest", null,
                "Unbalanced quotes");

        assertThatCode(() -> handler.handleParseError(issueCommentPayload, parseErrorContext))
                .doesNotThrowAnyException();
        verify(issue, never()).comment(anyString());
    }

    private static QuarkusGitHubBotConfigFile configFileWithFeatureEnabled() throws Exception {
        QuarkusGitHubBotConfigFile quarkusBotConfigFile = new QuarkusGitHubBotConfigFile();
        Field featuresField = QuarkusGitHubBotConfigFile.class.getDeclaredField("features");
        featuresField.setAccessible(true);
        featuresField.set(quarkusBotConfigFile, new HashSet<>(Set.of(Feature.RETEST_PULL_REQUEST_WORKFLOWS)));
        return quarkusBotConfigFile;
    }
}
