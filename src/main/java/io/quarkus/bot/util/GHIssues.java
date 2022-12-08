package io.quarkus.bot.util;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.json.JsonObject;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

public final class GHIssues {

    public static final String QUERY_PARTICIPANT_ERROR = "Unable to get participants for %s #%s of repository %s";

    public static final String ISSUE_TYPE = "issue";

    public static final String PULL_REQUEST_TYPE = "pullRequest";

    public static boolean hasLabel(GHIssue issue, String labelName) throws IOException {
        for (GHLabel label : issue.getLabels()) {
            if (labelName.equals(label.getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAreaLabel(GHIssue issue) throws IOException {
        for (GHLabel label : issue.getLabels()) {
            if (label.getName().startsWith(Labels.AREA_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> getParticipatingUsers(GHIssue issue, DynamicGraphQLClient gitHubGraphQLClient) {
        GHRepository repository = issue.getRepository();

        String objectType = (issue instanceof GHPullRequest) ? PULL_REQUEST_TYPE : ISSUE_TYPE;

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("owner", repository.getOwnerName());
            variables.put("repoName", repository.getName());
            variables.put("number", issue.getNumber());

            String graphqlRequest = """
                        query($owner: String! $repoName: String! $prNumber: Int!) {
                            repository(owner: $owner, name: $repoName) {
                              $objectType(number: $number) {
                                participants(first: 50) {
                                  edges {
                                    node {
                                      login
                                    }
                                  }
                                }
                              }
                            }
                          }
                    """.replace("$objectType", objectType);

            Response response = gitHubGraphQLClient.executeSync(graphqlRequest, variables);

            if (response == null) {
                // typically in tests where we don't mock the GraphQL client
                return Collections.emptySet();
            }

            if (response.hasError()) {
                String errorMsg = String.format(QUERY_PARTICIPANT_ERROR, objectType, issue.getNumber(),
                        repository.getFullName());
                throw new IllegalStateException(errorMsg + " : " + response.getErrors());
            }

            return response.getData().getJsonObject("repository")
                    .getJsonObject(objectType)
                    .getJsonObject("participants")
                    .getJsonArray("edges").stream()
                    .map(JsonObject.class::cast)
                    .map(obj -> obj.getJsonObject("node").getString("login"))
                    .collect(Collectors.toSet());
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(
                    String.format(QUERY_PARTICIPANT_ERROR, objectType, issue.getNumber(), repository.getFullName()), e);
        }
    }

    private GHIssues() {
    }
}
