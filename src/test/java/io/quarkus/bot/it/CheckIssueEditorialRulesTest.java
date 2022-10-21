package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.bot.CheckIssueEditorialRules;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GitHubAppTest
public class CheckIssueEditorialRulesTest {
    @Test
    void validZulipLinkConfirmation() throws IOException {
        given().github(mocks -> mocks.configFile("quarkus-github-bot.yml").fromString("features: [ ALL ]\n"))
                .when().payloadFromClasspath("/issue-opened-zulip.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    verify(mocks.issue(942074921))
                            .comment(CheckIssueEditorialRules.ZULIP_WARNING);
                    verifyNoMoreInteractions(mocks.ghObjects());
                });

    }
}
