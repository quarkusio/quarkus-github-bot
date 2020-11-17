package io.quarkiverse.githubapp;

import java.io.IOException;

import org.kohsuke.github.GHEventPayload;

import io.quarkiverse.githubapp.event.Issue;

public class TestIssueObserver {

    public void listen(@Issue.Opened GHEventPayload.Issue issue) throws IOException {
        issue.getIssue().comment("Test new comment 2");
    }
}
