package io.quarkus.bot;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHProject;
import org.kohsuke.github.GHProjectColumn;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;

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

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    Map<Integer, String> projectNodeIdMapping = new ConcurrentHashMap<>();
    Map<RepositoryProjectNumber, Long> classicProjectIdMapping = new ConcurrentHashMap<>();

    void issueLabeled(@Issue.Labeled GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            GitHub gitHub,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.PUSH_TO_PROJECTS.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        if (issuePayload.getOrganization() == null) {
            return;
        }

        doProjectPush("Issue #" + issuePayload.getIssue().getNumber() + ", label " + issuePayload.getLabel().getName(),
                issuePayload.getOrganization().getLogin(), issuePayload.getIssue(),
                issuePayload.getLabel(), issuePayload.getIssue().getNodeId(), true, quarkusBotConfigFile,
                gitHub, gitHubGraphQLClient);
    }

    void pullRequestLabeled(@PullRequest.Labeled GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            GitHub gitHub,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.PUSH_TO_PROJECTS.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        if (pullRequestPayload.getOrganization() == null) {
            return;
        }

        // do not add to projects pull requests related to infra, typically the backport pull requests
        if (Labels.matches(pullRequestPayload.getPullRequest().getLabels(), Labels.AREA_INFRA)) {
            return;
        }

        doProjectPush(
                "Pull request #" + pullRequestPayload.getPullRequest().getNumber() + ", label "
                        + pullRequestPayload.getLabel().getName(),
                pullRequestPayload.getOrganization().getLogin(), pullRequestPayload.getPullRequest(),
                pullRequestPayload.getLabel(), pullRequestPayload.getPullRequest().getNodeId(), false, quarkusBotConfigFile,
                gitHub, gitHubGraphQLClient);
    }

    private void doProjectPush(String context, String organization, GHIssue issue, GHLabel label, String itemId,
            boolean isIssue,
            QuarkusGitHubBotConfigFile quarkusBotConfigFile, GitHub gitHub,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        for (ProjectTriageRule projectTriageRule : quarkusBotConfigFile.projects.rules) {
            if (isIssue && !projectTriageRule.issues) {
                continue;
            }
            if (!isIssue && !projectTriageRule.pullRequests) {
                continue;
            }

            if (Labels.matchesName(projectTriageRule.labels, label.getName())) {
                String projectId = projectNodeIdMapping.computeIfAbsent(projectTriageRule.project,
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

        for (ProjectTriageRule projectTriageRule : quarkusBotConfigFile.projectsClassic.rules) {
            if (isIssue && !projectTriageRule.issues) {
                continue;
            }
            if (!isIssue && !projectTriageRule.pullRequests) {
                continue;
            }

            if (Labels.matchesName(projectTriageRule.labels, label.getName())) {
                RepositoryProjectNumber repositoryProjectNumber = new RepositoryProjectNumber(
                        issue.getRepository().getFullName(), projectTriageRule.project);
                Long projectId = classicProjectIdMapping.computeIfAbsent(repositoryProjectNumber,
                        rpn -> getClassicProjectId(issue, projectTriageRule.project));

                GHProject project = gitHub.getProject(projectId);
                Optional<GHProjectColumn> projectColumnOptional = project.listColumns().toList().stream()
                        .filter(c -> c.getName().equals(projectTriageRule.status))
                        .findFirst();

                if (projectColumnOptional.isEmpty()) {
                    throw new IllegalStateException("Unable to find column " + projectTriageRule.status + " in classic project "
                            + projectTriageRule.project);
                }

                GHProjectColumn projectColumn = projectColumnOptional.get();
                try {
                    projectColumn.createCard(issue);
                } catch (HttpException e) {
                    // the item is already part of the board and we can't add it
                }
            }
        }
    }

    private static Long getClassicProjectId(GHIssue issue, Integer projectNumber) {
        try {
            Optional<Long> projectId = issue.getRepository().listProjects().toList().stream()
                    .filter(p -> projectNumber == p.getNumber())
                    .map(p -> p.getId())
                    .findFirst();

            if (projectId.isEmpty()) {
                throw new IllegalStateException("Unable to find project id for project " + projectNumber
                        + " in repository " + issue.getRepository().getFullName());
            }

            return projectId.get();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to find project id for project " + projectNumber
                    + " in repository " + issue.getRepository().getFullName(), e);
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

            if (statusName != null && !isAlreadyInProject) {
                updateStatus(gitHubGraphQLClient, projectId, itemId, statusName);
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

    private static class RepositoryProjectNumber {

        private final String repository;
        private final Integer projectNumber;

        RepositoryProjectNumber(String repository, Integer projectNumber) {
            this.repository = repository;
            this.projectNumber = projectNumber;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RepositoryProjectNumber other = (RepositoryProjectNumber) obj;
            return Objects.equals(projectNumber, other.projectNumber) && Objects.equals(repository, other.repository);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectNumber, repository);
        }
    }
}
