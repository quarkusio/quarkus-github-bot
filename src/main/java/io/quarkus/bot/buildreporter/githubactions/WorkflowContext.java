package io.quarkus.bot.buildreporter.githubactions;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;

public class WorkflowContext {

    public final String repository;
    public final String type;
    public final String logContext;
    public final String htmlUrl;

    public WorkflowContext(GHIssue issue) {
        this.repository = issue.getRepository().getFullName();
        this.type = "Issue";
        this.logContext = this.type + " #" + issue.getNumber();
        this.htmlUrl = issue.getHtmlUrl().toString();
    }

    public WorkflowContext(GHPullRequest pullRequest) {
        this.repository = pullRequest.getRepository().getFullName();
        this.type = "Pull request";
        this.logContext = this.type + " #" + pullRequest.getNumber();
        this.htmlUrl = pullRequest.getHtmlUrl().toString();
    }

    public String getRepository() {
        return repository;
    }

    public String getType() {
        return type;
    }

    public String getLogContext() {
        return logContext;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }
}
