package io.quarkus.bot.workflow;

import java.nio.file.Path;

public class WorkflowConstants {

    public static final String BUILD_REPORTS_ARTIFACT_PREFIX = "build-reports-";
    public static final String MESSAGE_ID_ACTIVE = "<!-- Quarkus-GitHub-Bot/msg-id:workflow-run-status-active -->";
    public static final String MESSAGE_ID_HIDDEN = "<!-- Quarkus-GitHub-Bot/msg-id:workflow-run-status-hidden -->";
    public static final String PULL_REQUEST_NUMBER_PREFIX = "pull-request-number-";
    public static final String REPORT_ISSUE_NUMBER_PREFIX = "report-issue-number-";

    public static final Path BUILD_REPORT_PATH = Path.of("target", "build-report.json");
}
