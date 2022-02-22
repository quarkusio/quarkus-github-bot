package io.quarkus.bot.it;

import java.io.IOException;
import static org.mockito.Mockito.*;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;

import io.quarkus.bot.CheckIssueEditorialRules;
import io.quarkus.test.junit.QuarkusTest;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.when;

@QuarkusTest
@GitHubAppTest
public class CheckIssueEditorialRulesTest {
    @Test
    void validZulipLinkConfirmation() throws IOException {
        when().payloadFromClasspath("/issue-opened-zulip.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    verify(mocks.issue(942074921))

                            .comment(CheckIssueEditorialRules.ZULIP_WARNING);
                    verifyNoMoreInteractions(mocks.ghObjects());
                });

    }
}
