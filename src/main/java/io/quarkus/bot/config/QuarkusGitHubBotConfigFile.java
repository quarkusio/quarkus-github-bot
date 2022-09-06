package io.quarkus.bot.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class QuarkusGitHubBotConfigFile {

    @JsonDeserialize(as = HashSet.class)
    Set<Feature> features = new HashSet<>();

    public TriageConfig triage = new TriageConfig();

    public WorkflowRunAnalysisConfig workflowRunAnalysis = new WorkflowRunAnalysisConfig();

    public Projects projects = new Projects();

    public ProjectsClassic projectsClassic = new ProjectsClassic();

    public Workflows workflows = new Workflows();

    public static class TriageConfig {

        public List<TriageRule> rules = new ArrayList<>();

        public QE qe = new QE();

        public Discussions discussions = new Discussions();
    }

    public static class TriageRule {

        public String title;

        public String body;

        public String titleBody;

        public String expression;

        @JsonDeserialize(as = TreeSet.class)
        public Set<String> directories = new TreeSet<>();

        @JsonDeserialize(as = TreeSet.class)
        public Set<String> labels = new TreeSet<>();

        @JsonDeserialize(as = TreeSet.class)
        public Set<String> notify = new TreeSet<>();

        public String comment;

        public boolean notifyInPullRequest;

        public boolean allowSecondPass = false;
    }

    public static class QE {
        @JsonDeserialize(as = TreeSet.class)
        public Set<String> notify = new TreeSet<>();
    }

    public static class Discussions {

        /**
         * This is a list of numeric ids.
         * <p>
         * Note that it's a bit tricky to get this id as it's not present in the GraphQL API. You have to generate an event and
         * have a look at what is in the payload.
         */
        @JsonDeserialize(as = TreeSet.class)
        public Set<Long> monitoredCategories = new TreeSet<>();

        public boolean logCategories = false;
    }

    public static class WorkflowRunAnalysisConfig {

        @JsonDeserialize(as = HashSet.class)
        public Set<String> workflows = new HashSet<>();
    }

    public static class Workflows {

        public List<WorkflowApprovalRule> rules = new ArrayList<>();
    }

    public static class Projects {

        public List<ProjectTriageRule> rules = new ArrayList<>();
    }

    public static class ProjectsClassic {

        public List<ProjectTriageRule> rules = new ArrayList<>();
    }

    public static class ProjectTriageRule {

        @JsonDeserialize(as = TreeSet.class)
        public Set<String> labels = new TreeSet<>();

        public Integer project;

        public boolean issues = false;

        public boolean pullRequests = false;

        public String status;
    }

    public static class WorkflowApprovalRule {

        public WorkflowApprovalCondition allow;
        public WorkflowApprovalCondition unless;

    }

    public static class WorkflowApprovalCondition {
        public List<String> files;
        public UserRule users;

    }

    public static class UserRule {
        public Integer minContributions;
    }

    boolean isFeatureEnabled(Feature feature) {
        return features.contains(Feature.ALL) || features.contains(feature);
    }
}
