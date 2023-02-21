package io.quarkus.bot.it.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.mockito.Mockito;

import io.quarkus.bot.util.GHIssues;
import io.quarkus.bot.util.Mentions;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

public class GHIssuesTest {

    @ParameterizedTest
    @ValueSource(strings = { GHIssues.ISSUE_TYPE, GHIssues.PULL_REQUEST_TYPE })
    public void usersListIsFilteredWithGraphqlApiResult(String ghObjectType) throws ExecutionException, InterruptedException {

        DynamicGraphQLClient mockDynamicGraphQLClient = Mockito.mock(DynamicGraphQLClient.class);

        GHIssue mockGhObject = prepareGHIssueMock(ghObjectType);

        Response mockResponse = Mockito.mock(Response.class);
        when(mockResponse.getData()).thenReturn(buildMockJsonData(ghObjectType));
        when(mockResponse.hasError()).thenReturn(false);
        when(mockDynamicGraphQLClient.executeSync(anyString(), anyMap())).thenReturn(mockResponse);

        Mentions mentions = new Mentions();
        mentions.add(List.of("testUser1", "testUser2"), null);
        mentions.removeAlreadyParticipating(GHIssues.getParticipatingUsers(mockGhObject, mockDynamicGraphQLClient));

        verify(mockDynamicGraphQLClient, times(1)).executeSync(anyString(), anyMap());
        assertThat(mentions.getMentionsString()).isEqualTo("@testUser2");
    }

    @ParameterizedTest
    @MethodSource("provideExceptionTestData")
    public void exceptionIsThrownWhenGraphqlApiResponseHasError(String ghObjectType, String expectedErrorMesage)
            throws ExecutionException, InterruptedException {

        DynamicGraphQLClient mockDynamicGraphQLClient = Mockito.mock(DynamicGraphQLClient.class);

        GHIssue mockGhObject = prepareGHIssueMock(ghObjectType);

        Response mockResponse = Mockito.mock(Response.class);
        when(mockResponse.hasError()).thenReturn(true);
        when(mockDynamicGraphQLClient.executeSync(anyString(), anyMap())).thenReturn(mockResponse);

        Exception exception = assertThrows(IllegalStateException.class,
                () -> GHIssues.getParticipatingUsers(mockGhObject, mockDynamicGraphQLClient));
        assertThat(exception.getMessage().contains(expectedErrorMesage));

        verify(mockDynamicGraphQLClient, times(1)).executeSync(anyString(), anyMap());
    }

    @ParameterizedTest
    @MethodSource("provideExceptionTestData")
    public void exceptionIsThrownWhenGraphqlApiThrowsException(String ghObjectType, String expectedErrorMesage)
            throws ExecutionException, InterruptedException {

        DynamicGraphQLClient mockDynamicGraphQLClient = Mockito.mock(DynamicGraphQLClient.class);

        GHIssue mockGhObject = prepareGHIssueMock(ghObjectType);

        when(mockDynamicGraphQLClient.executeSync(anyString(), anyMap())).thenThrow(ExecutionException.class);

        Exception exception = assertThrows(IllegalStateException.class,
                () -> GHIssues.getParticipatingUsers(mockGhObject, mockDynamicGraphQLClient));
        assertThat(exception.getMessage().contains(expectedErrorMesage));

        verify(mockDynamicGraphQLClient, times(1)).executeSync(anyString(), anyMap());
    }

    private static Stream<Arguments> provideExceptionTestData() {

        String issueErrorMessage = String.format(GHIssues.QUERY_PARTICIPANT_ERROR, GHIssues.ISSUE_TYPE, TEST_ISSUE_NUMBER,
                TEST_OWNER_NAME + ":" + TEST_REPO_NAME);
        String pullRequestErrorMessage = String.format(GHIssues.QUERY_PARTICIPANT_ERROR, GHIssues.PULL_REQUEST_TYPE,
                TEST_ISSUE_NUMBER,
                TEST_OWNER_NAME + ":" + TEST_REPO_NAME);

        return Stream.of(
                Arguments.of(GHIssues.ISSUE_TYPE, issueErrorMessage),
                Arguments.of(GHIssues.PULL_REQUEST_TYPE, pullRequestErrorMessage));
    }

    private final static String TEST_OWNER_NAME = "testOwnerName";

    private final static String TEST_REPO_NAME = "testOwnerName";

    private final static Integer TEST_ISSUE_NUMBER = 123456;

    private GHIssue prepareGHIssueMock(String ghObjectType) {

        GHIssue mockGhObject = null;
        if (GHIssues.ISSUE_TYPE.equals(ghObjectType)) {
            mockGhObject = Mockito.mock(GHIssue.class);
        } else if (GHIssues.PULL_REQUEST_TYPE.equals(ghObjectType)) {
            mockGhObject = Mockito.mock(GHPullRequest.class);
        } else {
            throw new IllegalArgumentException("Input type must be either issue or pullRequest");
        }

        GHRepository mockGHRepository = Mockito.mock(GHRepository.class);
        when(mockGHRepository.getOwnerName()).thenReturn(TEST_OWNER_NAME);
        when(mockGHRepository.getName()).thenReturn(TEST_REPO_NAME);
        when(mockGHRepository.getFullName()).thenReturn(TEST_OWNER_NAME + ":" + TEST_REPO_NAME);

        when(mockGhObject.getRepository()).thenReturn(mockGHRepository);
        when(mockGhObject.getNumber()).thenReturn(TEST_ISSUE_NUMBER);

        return mockGhObject;
    }

    private JsonObject buildMockJsonData(String ghObjectType) {

        String mockJsonStr = """
                  {
                    "repository": {
                      "$ghObjectType": {
                        "participants": {
                          "edges": [
                            {
                              "node": {
                                "login": "testUser1"
                              }
                            }
                          ]
                        }
                      }
                    }
                }
                  """.replace("$ghObjectType", ghObjectType);

        JsonReader jsonReader = Json.createReader(new StringReader(mockJsonStr));
        return jsonReader.readObject();
    }
}
