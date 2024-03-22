package io.quarkus.bot;

import java.io.IOException;
import java.util.Comparator;

import jakarta.inject.Inject;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GitHub;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.WorkflowRun;
import io.quarkus.bot.buildreporter.githubactions.BuildReporterConfig;
import io.quarkus.bot.buildreporter.githubactions.BuildReporterEventHandler;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

public class AnalyzeWorkflowRunResults {

    @Inject
    BuildReporterEventHandler buildReporterEventHandler;

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void analyzeWorkflowResults(@WorkflowRun.Completed @WorkflowRun.Requested GHEventPayload.WorkflowRun workflowRunPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            GitHub gitHub, DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.ANALYZE_WORKFLOW_RUN_RESULTS.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        BuildReporterConfig buildReporterConfig = BuildReporterConfig.builder()
                .dryRun(quarkusBotConfig.isDryRun())
                .monitoredWorkflows(quarkusBotConfigFile.workflowRunAnalysis.workflows)
                .workflowJobComparator(QuarkusWorkflowJobComparator.INSTANCE)
                .enableDevelocity(quarkusBotConfigFile.develocity.enabled)
                .develocityUrl(quarkusBotConfigFile.develocity.url)
                .build();

        buildReporterEventHandler.handle(workflowRunPayload, buildReporterConfig, gitHub, gitHubGraphQLClient);
    }

    private final static class QuarkusWorkflowJobComparator implements Comparator<GHWorkflowJob> {

        private static final QuarkusWorkflowJobComparator INSTANCE = new QuarkusWorkflowJobComparator();

        @Override
        public int compare(GHWorkflowJob o1, GHWorkflowJob o2) {
            int order1 = getOrder(o1.getName());
            int order2 = getOrder(o2.getName());

            if (order1 == order2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }

            return order1 - order2;
        }

        private static int getOrder(String jobName) {
            if (jobName.startsWith("Initial JDK")) {
                return 1;
            }
            if (jobName.startsWith("Calculate Test Jobs")) {
                return 2;
            }
            if (jobName.startsWith("JVM Tests - ")) {
                if (jobName.contains("Windows")) {
                    return 12;
                }
                return 11;
            }
            if (jobName.startsWith("Maven Tests - ")) {
                if (jobName.contains("Windows")) {
                    return 22;
                }
                return 21;
            }
            if (jobName.startsWith("Gradle Tests - ")) {
                if (jobName.contains("Windows")) {
                    return 32;
                }
                return 31;
            }
            if (jobName.startsWith("Devtools Tests - ")) {
                if (jobName.contains("Windows")) {
                    return 42;
                }
                return 41;
            }
            if (jobName.startsWith("Kubernetes Tests - ")) {
                if (jobName.contains("Windows")) {
                    return 52;
                }
                return 51;
            }
            if (jobName.startsWith("Quickstarts Compilation")) {
                return 61;
            }
            if (jobName.startsWith("MicroProfile TCKs Tests")) {
                return 71;
            }
            if (jobName.startsWith("Native Tests - ")) {
                if (jobName.contains("Windows")) {
                    return 82;
                }
                return 81;
            }

            return 200;
        }
    }
}
