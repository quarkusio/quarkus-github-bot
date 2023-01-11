package io.quarkus.bot.it;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.bot.CheckTriageBackportContext;
import io.quarkus.bot.util.Strings;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GitHubAppTest
public class CheckTriageBackportContextTest {

    @Test
    void testLabelBackportWarningConfirmation() throws IOException {
        String warningMsg = String.format(CheckTriageBackportContext.LABEL_BACKPORT_WARNING, "triage/backport-whatever");
        String expectedComment = Strings.commentByBot("@test-github-user " + warningMsg);

        given().github(mocks -> mocks.configFile("quarkus-github-bot.yml").fromString("features: [ ALL ]\n"))
                .when().payloadFromString(getSampleIssueLabelTriageBackportPayload())
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    verify(mocks.issue(1234567890))
                            .comment(expectedComment.toString());
                    verifyNoMoreInteractions(mocks.ghObjects());
                });

    }

    private static String getSampleIssueLabelTriageBackportPayload() {
        return """
                {
                    "action": "labeled",
                    "issue": {
                      "id": 1234567890,
                      "number": 123,
                      "labels": [
                        {
                          "name": "triage/backport-whatever"
                        }
                      ]
                    },
                    "label": {
                      "name": "triage/backport-whatever"
                    },
                    "repository": {

                    },
                    "sender": {
                      "login": "test-github-user"
                    }
                  }""";
    }
}
