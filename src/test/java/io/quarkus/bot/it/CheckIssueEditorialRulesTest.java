package io.quarkus.bot.it;

import java.io.IOException;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;

import io.quarkiverse.githubapp.testing.GitHubAppTestingResource;
import io.quarkus.bot.CheckIssueEditorialRules;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.when;

@QuarkusTest
@QuarkusTestResource(GitHubAppTestingResource.class)
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
