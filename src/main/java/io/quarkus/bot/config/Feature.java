package io.quarkus.bot.config;

public enum Feature {

    ALL,
    ADD_BRANCH_TO_PULL_REQUEST_TITLE,
    ANALYZE_WORKFLOW_RUN_RESULTS,
    CHECK_EDITORIAL_RULES,
    CHECK_CONTRIBUTION_RULES,
    NOTIFY_QE,
    QUARKUS_REPOSITORY_WORKFLOW,
    SET_AREA_LABEL_COLOR,
    SET_TRIAGE_BACKPORT_LABEL_COLOR,
    PULL_REQUEST_GUARDED_BRANCHES,
    TRIAGE_ISSUES_AND_PULL_REQUESTS,
    TRIAGE_DISCUSSIONS,
    PUSH_TO_PROJECTS,
    APPROVE_WORKFLOWS,
    CHECK_SIMILAR_GITHUB_HANDLES,
    SET_MILESTONE_DUE_ON_ON_CLOSE;

    public boolean isEnabled(QuarkusGitHubBotConfigFile quarkusBotConfigFile) {
        if (quarkusBotConfigFile == null) {
            return false;
        }

        return quarkusBotConfigFile.isFeatureEnabled(this);
    }
}
