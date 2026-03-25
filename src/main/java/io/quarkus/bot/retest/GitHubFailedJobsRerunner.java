package io.quarkus.bot.retest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHWorkflowRun;

import io.quarkiverse.githubapp.InstallationTokenProvider;
import io.quarkiverse.githubapp.JavaHttpClientFactory;

/**
 * GitHub-backed {@link FailedJobsRerunner} implementation using the REST endpoint that reruns only failed jobs.
 */
@Singleton
class GitHubFailedJobsRerunner implements FailedJobsRerunner {

    private static final String ACCEPT_HEADER = "application/vnd.github+json";
    private static final String USER_AGENT = "quarkus-github-bot";
    private static final Duration RERUN_FAILED_JOBS_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    InstallationTokenProvider installationTokenProvider;

    @Inject
    JavaHttpClientFactory javaHttpClientFactory;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = javaHttpClientFactory.create();
    }

    @Override
    public void rerunFailedJobs(GHEventPayload.IssueComment issueCommentPayload, GHWorkflowRun workflowRun) {
        String installationToken = installationTokenProvider.getInstallationToken(issueCommentPayload.getInstallation().getId())
                .token();

        HttpRequest request = HttpRequest.newBuilder(rerunFailedJobsUri(issueCommentPayload, workflowRun))
                .header("Accept", ACCEPT_HEADER)
                .header("Authorization", "Bearer " + installationToken)
                .header("User-Agent", USER_AGENT)
                .timeout(RERUN_FAILED_JOBS_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw RetestCommandException.rerunFailedJobsFailed(workflowRun.getId(), response.statusCode(), null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RetestCommandException.rerunFailedJobsFailed(workflowRun.getId(), null, e);
        } catch (IOException e) {
            throw RetestCommandException.rerunFailedJobsFailed(workflowRun.getId(), null, e);
        }
    }

    URI rerunFailedJobsUri(GHEventPayload.IssueComment issueCommentPayload, GHWorkflowRun workflowRun) {
        String normalizedApiUrl = normalizeApiUrl(gitHubApiUrl(issueCommentPayload));

        // TODO switch to GHWorkflowRun.rerunFailedJobs() when Hub4j adds native support for rerunning failed jobs.
        return URI.create(normalizedApiUrl + "repos/" + issueCommentPayload.getRepository().getOwnerName() + "/"
                + issueCommentPayload.getRepository().getName()
                + "/actions/runs/" + workflowRun.getId() + "/rerun-failed-jobs");
    }

    @SuppressWarnings("deprecation")
    private static String gitHubApiUrl(GHEventPayload.IssueComment issueCommentPayload) {
        return issueCommentPayload.getRoot().getApiUrl();
    }

    private static String normalizeApiUrl(String apiUrl) {
        return apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
    }
}
