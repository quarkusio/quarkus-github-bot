package io.quarkus.bot.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.json.JsonObject;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

public class IssueExtractor {

    private final Pattern pattern;

    public IssueExtractor(String repository) {
        pattern = Pattern.compile(
                "\\b(?:(?:fix(?:e[sd])?|(?:(?:resolve|close)[sd]?))):?\\s+(?:https?:\\/\\/github.com\\/"
                        + Pattern.quote(repository) + "\\/issues\\/|#)(\\d+)",
                Pattern.CASE_INSENSITIVE);
    }

    public Set<Integer> extractIssueNumbers(GHPullRequest pullRequest, DynamicGraphQLClient gitHubGraphQLClient) {
        Set<Integer> result = new TreeSet<>();

        String prBody = pullRequest.getBody();
        result.addAll(extractFromPRContent(prBody));
        result.addAll(extractWithGraphQLApi(pullRequest, gitHubGraphQLClient));

        return result;
    }

    public Set<Integer> extractFromPRContent(String content) {
        Set<Integer> result = new TreeSet<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            Integer issueNumber = Integer.valueOf(matcher.group(1));
            result.add(issueNumber);
        }
        return result;
    }

    public Set<Integer> extractWithGraphQLApi(GHPullRequest pullRequest, DynamicGraphQLClient gitHubGraphQLClient) {

        GHRepository repository = pullRequest.getRepository();
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("owner", repository.getOwnerName());
            variables.put("repoName", repository.getName());
            variables.put("prNumber", pullRequest.getNumber());

            Response response = gitHubGraphQLClient.executeSync("""
                        query($owner: String! $repoName: String! $prNumber: Int!) {
                            repository(owner: $owner, name: $repoName) {
                              pullRequest(number: $prNumber) {
                                closingIssuesReferences(first: 50) {
                                  edges {
                                    node {
                                      number
                                      repository {
                                        nameWithOwner
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          }
                    """, variables);

            if (response.hasError()) {
                throw new IllegalStateException(
                        "Unable to get closingIssuesReferences for PR #" + pullRequest.getNumber() + " of repository "
                                + repository.getFullName() + ": " + response.getErrors());
            }

            Set<Integer> issueNumbers = response.getData().getJsonObject("repository")
                    .getJsonObject("pullRequest")
                    .getJsonObject("closingIssuesReferences")
                    .getJsonArray("edges").stream()
                    .map(JsonObject.class::cast)
                    .filter(obj -> pullRequest.getRepository().getFullName()
                            .equals(obj.getJsonObject("node").getJsonObject("repository").getString("nameWithOwner")))
                    .map(obj -> obj.getJsonObject("node").getInt("number"))
                    .collect(Collectors.toSet());

            return issueNumbers;
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(
                    "Unable to get closingIssuesReferences for PR #" + pullRequest.getNumber() + " of repository "
                            + repository.getFullName(),
                    e);
        }
    }
}
