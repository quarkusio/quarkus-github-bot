package io.quarkus.bot.it;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.bot.util.Labels;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;

import java.io.IOException;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@QuarkusTest
@GitHubAppTest
public class IssueOpenedTest {

    @Test
    void triage() throws IOException {
        given().github(mocks -> mocks.configFileFromString(
                "quarkus-bot.yml",
                "triage:\n"
                        + "  rules:\n"
                        + "    - title: test\n"
                        + "      labels: [area/test1, area/test2]"))
                .when().payloadFromClasspath("/issue-opened.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    verify(mocks.issue(750705278))
                            .addLabels("area/test1", "area/test2");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageComment() throws IOException {
        given().github(mocks -> mocks.configFileFromString(
                "quarkus-bot.yml",
                "triage:\n"
                        + "  rules:\n"
                        + "    - title: test\n"
                        + "      comment: 'This is a security issue'"))
                .when().payloadFromClasspath("/issue-opened.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    verify(mocks.issue(750705278))
                            .comment("This is a security issue");
                    verify(mocks.issue(750705278))
                            .addLabels(Labels.TRIAGE_NEEDS_TRIAGE);
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

}
