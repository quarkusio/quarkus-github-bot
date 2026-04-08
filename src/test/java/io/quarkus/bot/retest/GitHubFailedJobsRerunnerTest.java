package io.quarkus.bot.retest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.mockito.ArgumentCaptor;

import io.quarkiverse.githubapp.InstallationTokenProvider;
import io.quarkiverse.githubapp.JavaHttpClientFactory;

class GitHubFailedJobsRerunnerTest {

    @Test
    void shouldPostToRerunFailedJobsEndpoint() throws Exception {
        GitHubFailedJobsRerunner rerunner = new GitHubFailedJobsRerunner();
        rerunner.installationTokenProvider = mock(InstallationTokenProvider.class);
        rerunner.javaHttpClientFactory = mock(JavaHttpClientFactory.class);

        HttpClient httpClient = mock(HttpClient.class);
        when(rerunner.javaHttpClientFactory.create()).thenReturn(httpClient);
        rerunner.init();

        GHEventPayload.IssueComment issueCommentPayload = issueCommentPayload();
        GHWorkflowRun workflowRun = workflowRun(2860832197L);

        InstallationTokenProvider.InstallationToken installationToken = mock(InstallationTokenProvider.InstallationToken.class);
        when(rerunner.installationTokenProvider.getInstallationToken(13173124L)).thenReturn(installationToken);
        when(installationToken.token()).thenReturn("test-token");

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        rerunner.rerunFailedJobs(issueCommentPayload, workflowRun);

        HttpRequest request = requestCaptor.getValue();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString())
                .isEqualTo("https://ghe.example/api/v3/repos/quarkusio/quarkus-bot/actions/runs/2860832197/rerun-failed-jobs");
        assertThat(request.headers().firstValue("Accept")).hasValue("application/vnd.github+json");
        assertThat(request.headers().firstValue("Authorization")).hasValue("Bearer test-token");
        assertThat(request.headers().firstValue("User-Agent")).hasValue("quarkus-github-bot");
        assertThat(request.timeout()).hasValue(Duration.ofSeconds(30));
    }

    @Test
    void shouldThrowClearExceptionWhenGitHubRejectsTheRequest() throws Exception {
        GitHubFailedJobsRerunner rerunner = new GitHubFailedJobsRerunner();
        rerunner.installationTokenProvider = mock(InstallationTokenProvider.class);
        rerunner.javaHttpClientFactory = mock(JavaHttpClientFactory.class);

        HttpClient httpClient = mock(HttpClient.class);
        when(rerunner.javaHttpClientFactory.create()).thenReturn(httpClient);
        rerunner.init();

        GHEventPayload.IssueComment issueCommentPayload = issueCommentPayload();
        GHWorkflowRun workflowRun = workflowRun(2860832197L);

        InstallationTokenProvider.InstallationToken installationToken = mock(InstallationTokenProvider.InstallationToken.class);
        when(rerunner.installationTokenProvider.getInstallationToken(13173124L)).thenReturn(installationToken);
        when(installationToken.token()).thenReturn("test-token");

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> rerunner.rerunFailedJobs(issueCommentPayload, workflowRun))
                .isInstanceOf(RetestCommandException.class)
                .hasMessageContaining("workflow run #2860832197")
                .hasMessageContaining("status 500");
    }

    @Test
    void shouldTargetTheIssueCommentRepository() throws Exception {
        GitHubFailedJobsRerunner rerunner = new GitHubFailedJobsRerunner();
        rerunner.installationTokenProvider = mock(InstallationTokenProvider.class);
        rerunner.javaHttpClientFactory = mock(JavaHttpClientFactory.class);

        HttpClient httpClient = mock(HttpClient.class);
        when(rerunner.javaHttpClientFactory.create()).thenReturn(httpClient);
        rerunner.init();

        GHEventPayload.IssueComment issueCommentPayload = issueCommentPayload();
        GHWorkflowRun workflowRun = workflowRun(2860832197L, "someone-else", "other-repo");

        InstallationTokenProvider.InstallationToken installationToken = mock(InstallationTokenProvider.InstallationToken.class);
        when(rerunner.installationTokenProvider.getInstallationToken(13173124L)).thenReturn(installationToken);
        when(installationToken.token()).thenReturn("test-token");

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        rerunner.rerunFailedJobs(issueCommentPayload, workflowRun);

        assertThat(requestCaptor.getValue().uri().toString())
                .isEqualTo("https://ghe.example/api/v3/repos/quarkusio/quarkus-bot/actions/runs/2860832197/rerun-failed-jobs");
    }

    private static GHEventPayload.IssueComment issueCommentPayload() throws Exception {
        GHEventPayload.IssueComment issueCommentPayload = mock(GHEventPayload.IssueComment.class);
        GHAppInstallation installation = new GHAppInstallation() {
            @Override
            public long getId() {
                return 13173124L;
            }
        };
        GitHub gitHub = mock(GitHub.class);
        GHRepository repository = mock(GHRepository.class);

        when(issueCommentPayload.getInstallation()).thenReturn(installation);
        when(issueCommentPayload.getRoot()).thenReturn(gitHub);
        when(issueCommentPayload.getRepository()).thenReturn(repository);
        when(gitHub.getApiUrl()).thenReturn("https://ghe.example/api/v3");
        when(repository.getOwnerName()).thenReturn("quarkusio");
        when(repository.getName()).thenReturn("quarkus-bot");

        return issueCommentPayload;
    }

    private static GHWorkflowRun workflowRun(long workflowRunId) {
        return workflowRun(workflowRunId, null, null);
    }

    private static GHWorkflowRun workflowRun(long workflowRunId, String ownerName, String repositoryName) {
        return new GHWorkflowRun() {
            @Override
            public long getId() {
                return workflowRunId;
            }

            @Override
            public GHRepository getRepository() {
                if (ownerName == null || repositoryName == null) {
                    return null;
                }

                GHRepository repository = mock(GHRepository.class);
                when(repository.getOwnerName()).thenReturn(ownerName);
                when(repository.getName()).thenReturn(repositoryName);
                return repository;
            }

            @Override
            public java.net.URL getHtmlUrl() {
                return null;
            }
        };
    }
}
