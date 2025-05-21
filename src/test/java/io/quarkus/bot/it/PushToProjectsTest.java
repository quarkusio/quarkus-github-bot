package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GitHubAppTest
public class PushToProjectsTest {

    /**
     * This is a small little test which ensures that if a payload does not have an organisation, PushToProjects does not crash.
     */
    @Test
    void pullRequestLabeledWithNullOrganization() throws IOException {

        given()
                .github(mocks -> mocks.configFile("quarkus-github-bot.yml")
                        .fromString("""
                                features: [ PUSH_TO_PROJECTS ]
                                project:
                                    rules:
                                      - labels: [area/hibernate-validator]
                                        project: 1
                                        issues: true
                                        pullRequests: false
                                        status: Todo"""))
                .when().payloadFromClasspath("/pullrequest-labeled-no-organization.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    // Without an organization, nothing should happen except a not-crash
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

}
