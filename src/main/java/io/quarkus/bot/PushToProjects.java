package io.quarkus.bot;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile.ProjectTriageRule;
import io.quarkus.bot.util.Labels;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

public class PushToProjects {

    private static final Logger LOG = Logger.getLogger(PushToProjects.class);

    private static final String STATUS_FIELD_NAME = "Status";
    private static final String DEFAULT_STATUS_NAME = "Todo";

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    Map<Integer, String> projectMapping = new ConcurrentHashMap<>();

    void issueLabeled(@Issue.Labeled GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.PUSH_TO_PROJECTS.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        doProjectPush("Issue #" + issuePayload.getIssue().getNumber() + ", label " + issuePayload.getLabel().getName(),
                issuePayload.getOrganization().getLogin(),
                issuePayload.getLabel(), issuePayload.getIssue().getNodeId(), true, quarkusBotConfigFile, gitHubGraphQLClient);
    }

    void pullRequestLabeled(@PullRequest.Labeled GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.PUSH_TO_PROJECTS.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        doProjectPush(
                "Pull request #" + pullRequestPayload.getPullRequest().getNumber() + ", label "
                        + pullRequestPayload.getLabel().getName(),
                pullRequestPayload.getOrganization().getLogin(),
                pullRequestPayload.getLabel(), pullRequestPayload.getPullRequest().getNodeId(), true, quarkusBotConfigFile,
                gitHubGraphQLClient);
    }

    private void doProjectPush(String context, String organization, GHLabel label, String itemId, boolean isIssue,
            QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) {
        for (ProjectTriageRule projectTriageRule : quarkusBotConfigFile.projects.rules) {
            if (isIssue && !projectTriageRule.issues) {
                continue;
            }
            if (!isIssue && !projectTriageRule.pullRequests) {
                continue;
            }

            if (Labels.matches(projectTriageRule.labels, label.getName())) {
                String projectId = projectMapping.computeIfAbsent(projectTriageRule.project,
                        p -> getProjectNodeId(gitHubGraphQLClient, organization, p));

                if (projectId == null) {
                    LOG.error(context + " - Unable to find a project node id for project " + projectTriageRule.project);
                    return;
                }

                if (!quarkusBotConfig.isDryRun()) {
                    addItemToProject(gitHubGraphQLClient, projectId, itemId, projectTriageRule.status);
                } else {
                    LOG.info(context + " - Add " + context + " to project " + projectTriageRule.project);
                }
            }
        }
    }

    private static String getProjectNodeId(DynamicGraphQLClient gitHubGraphQLClient, String organization, Integer number) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("organization", organization);
            variables.put("number", number);

            Response response = gitHubGraphQLClient.executeSync("""
                    query($organization: String! $number: Int!){
                      organization(login: $organization){
                        projectV2(number: $number) {
                          id
                        }
                      }
                    }""", variables);

            if (response.hasError()) {
                throw new IllegalStateException(
                        "Unable to get project node id for " + organization + "#" + number + ": " + response.getErrors());
            }

            String projectNodeId = response.getData().getJsonObject("organization").getJsonObject("projectV2").getString("id");

            if (projectNodeId == null) {
                throw new IllegalStateException(
                        "Unable to get project node id for " + organization + "#" + number);
            }

            return projectNodeId;
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Unable to get project node id for " + organization + "#" + number, e);
        }
    }

    private static void addItemToProject(DynamicGraphQLClient gitHubGraphQLClient, String projectId, String contentId,
            String statusName) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("projectId", projectId);
            variables.put("contentId", contentId);

            Response response = gitHubGraphQLClient.executeSync("""
                    mutation($projectId: ID!, $contentId: ID!) {
                      addProjectV2ItemById(input: {projectId: $projectId contentId: $contentId}) {
                        item {
                          id
                          fieldValues(first: 15) {
                            nodes {
                              ... on ProjectV2ItemFieldSingleSelectValue {
                                name
                                field {
                                  ... on ProjectV2FieldCommon {
                                    name
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }""", variables);

            if (response.hasError()) {
                throw new IllegalStateException(
                        "Unable to add item " + contentId + " to project " + projectId + ": " + response.getErrors());
            }

            String itemId = response.getData().getJsonObject("addProjectV2ItemById").getJsonObject("item").getString("id");

            boolean isAlreadyInProject = response.getData().getJsonObject("addProjectV2ItemById").getJsonObject("item")
                    .getJsonObject("fieldValues").getJsonArray("nodes")
                    .stream()
                    .map(o -> o.asJsonObject())
                    .filter(o -> o.containsKey("field"))
                    .map(o -> o.getJsonObject("field"))
                    .filter(o -> o.containsKey("name"))
                    .anyMatch(o -> STATUS_FIELD_NAME.equals(o.getString("name")));

            if (!isAlreadyInProject) {
                updateStatus(gitHubGraphQLClient, projectId, itemId, statusName == null ? DEFAULT_STATUS_NAME : statusName);
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Unable to add item " + contentId + " to project " + projectId, e);
        }
    }

    private static void updateStatus(DynamicGraphQLClient gitHubGraphQLClient, String projectId, String itemId,
            String statusName) {
        Map.Entry<String, String> statusFieldValue = getStatusFieldValue(gitHubGraphQLClient, projectId, statusName);

        Map<String, Object> variables = new HashMap<>();
        variables.put("projectId", projectId);
        variables.put("itemId", itemId);
        variables.put("fieldId", statusFieldValue.getKey());
        variables.put("optionId", statusFieldValue.getValue());

        try {
            Response response = gitHubGraphQLClient.executeSync("""
                    mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $optionId: String!) {
                      updateProjectV2ItemFieldValue(
                        input: {
                          projectId: $projectId
                          itemId: $itemId
                          fieldId: $fieldId
                          value: {
                            singleSelectOptionId: $optionId
                          }
                        }
                      ) {
                        projectV2Item {
                          id
                        }
                      }
                    }""", variables);

            if (response.hasError()) {
                throw new IllegalStateException("Unable to update Status for item " + itemId + " in project " + projectId + ": "
                        + response.getErrors());
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Unable to update Status for item " + itemId + " in project " + projectId, e);
        }
    }

    @CacheResult(cacheName = "PushToProject.getStatusFieldValue")
    static Map.Entry<String, String> getStatusFieldValue(DynamicGraphQLClient gitHubGraphQLClient, @CacheKey String projectId,
            @CacheKey String statusName) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("projectId", projectId);

            Response response = gitHubGraphQLClient.executeSync("""
                    query($projectId: ID!) {
                      node(id: $projectId) {
                        ... on ProjectV2 {
                          fields(first: 20) {
                            nodes {
                              ... on ProjectV2SingleSelectField {
                                id
                                name
                                options {
                                  id
                                  name
                                }
                              }
                            }
                          }
                        }
                      }
                    }""", variables);

            if (response.hasError()) {
                throw new IllegalStateException(
                        "Unable to get Status field for project " + projectId + ": " + response.getErrors());
            }

            Optional<JsonObject> statusFieldOptional = response.getData().getJsonObject("node").getJsonObject("fields")
                    .getJsonArray("nodes")
                    .stream()
                    .map(o -> o.asJsonObject())
                    .filter(o -> o.containsKey("name"))
                    .filter(o -> STATUS_FIELD_NAME.equals(o.getString("name")))
                    .findFirst();

            if (statusFieldOptional.isEmpty()) {
                throw new IllegalStateException(
                        "Unable to find Status field for project " + projectId);
            }

            JsonObject statusField = statusFieldOptional.get();
            String statusFieldId = statusField.getString("id");
            Optional<String> statusValueId = statusField.getJsonArray("options").stream()
                    .map(o -> o.asJsonObject())
                    .filter(o -> statusName.equals(o.getString("name")))
                    .map(o -> o.getString("id"))
                    .findFirst();

            if (statusValueId.isEmpty()) {
                throw new IllegalStateException(
                        "Unable to find Status option with name " + statusName + " for project " + projectId);
            }

            return Map.entry(statusFieldId, statusValueId.get());
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Unable to get Status field for project " + projectId, e);
        }
    }
}
