package io.quarkiverse.githubapp;

import java.io.IOException;

import org.kohsuke.github.GHEventPayload;

import io.quarkiverse.githubapp.event.Issue;

public class TestIssueObserver {

    public void listen(@Issue.Opened @Issue.Edited GHEventPayload.Issue issuePayload) throws IOException {
        issuePayload.getIssue().comment("Test new comment 2");
    }
}
