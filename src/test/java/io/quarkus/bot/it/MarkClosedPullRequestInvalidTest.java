package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;

import io.quarkiverse.githubapp.testing.GitHubAppTestingResource;
import io.quarkus.bot.util.Labels;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(GitHubAppTestingResource.class)
public class MarkClosedPullRequestInvalidTest {
    @Test
    void handleLabels() throws IOException {
        when().payloadFromClasspath("/pullrequest-closed.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(691467750))
                            .addLabels(Labels.TRIAGE_INVALID+"triage/invalid");
                    verify(mocks.pullRequest(691467750))
                            .removeLabel(Labels.TRIAGE_BACKPORT_PREFIX);
                    verifyNoMoreInteractions(mocks.ghObjects());


                });

    }
}
