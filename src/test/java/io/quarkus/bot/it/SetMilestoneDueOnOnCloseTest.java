package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHMilestone;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GitHubAppTest
public class SetMilestoneDueOnOnCloseTest {

    @Test
    void setDueOnWhenMilestoneClosed() throws IOException {
        GHMilestone milestoneMock = mock(GHMilestone.class);

        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml").fromString("features: [ ALL ]\n");
            when(mocks.repository("gsmet/quarkus-bot-java-playground").getMilestone(2))
                    .thenReturn(milestoneMock);
        })
                .when()
                .payloadFromClasspath("/milestone-closed.json")
                .rawEvent("milestone")
                .then().github(mocks -> {
                    verify(milestoneMock).setDueOn(any(Date.class));
                });
    }

    @Test
    void doNotSetDueOnWhenFeatureDisabled() throws IOException {
        GHMilestone milestoneMock = mock(GHMilestone.class);

        given().github(mocks -> {
            mocks.configFile("quarkus-github-bot.yml")
                    .fromString("features: [ TRIAGE_ISSUES_AND_PULL_REQUESTS ]\n");
            when(mocks.repository("gsmet/quarkus-bot-java-playground").getMilestone(2))
                    .thenReturn(milestoneMock);
        })
                .when()
                .payloadFromClasspath("/milestone-closed.json")
                .rawEvent("milestone")
                .then().github(mocks -> {
                    verifyNoMoreInteractions(milestoneMock);
                });
    }
}
