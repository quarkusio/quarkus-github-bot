package io.quarkus.bot.config;

public enum Feature {

    ALL,
    ANALYZE_WORKFLOW_RUN_RESULTS,
    CHECK_EDITORIAL_RULES,
    CHECK_CONTRIBUTION_RULES,
    NOTIFY_QE,
    QUARKUS_REPOSITORY_WORKFLOW,
    SET_AREA_LABEL_COLOR,
    TRIAGE_ISSUES_AND_PULL_REQUESTS,
    TRIAGE_DISCUSSIONS,
    PUSH_TO_PROJECTS,
    APPROVE_WORKFLOWS;

    public boolean isEnabled(QuarkusGitHubBotConfigFile quarkusBotConfigFile) {
        if (quarkusBotConfigFile == null) {
            return false;
        }

        return quarkusBotConfigFile.isFeatureEnabled(this);
    }
}
