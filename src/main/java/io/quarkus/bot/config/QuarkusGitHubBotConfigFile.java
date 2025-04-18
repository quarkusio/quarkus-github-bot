package io.quarkus.bot.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Note: a subset of this class is present in action-build-reporter,
 * so be careful when updating existing features.
 */
public class QuarkusGitHubBotConfigFile {

    @JsonDeserialize(as = HashSet.class)
    Set<Feature> features = new HashSet<>();

    public TriageConfig triage = new TriageConfig();

    public WorkflowRunAnalysisConfig workflowRunAnalysis = new WorkflowRunAnalysisConfig();

    public Projects projects = new Projects();

    public ProjectsClassic projectsClassic = new ProjectsClassic();

    public Workflows workflows = new Workflows();

    public Develocity develocity = new Develocity();

    public static class TriageConfig {

        public List<TriageRule> rules = new ArrayList<>();

        public List<GuardedBranch> guardedBranches = new ArrayList<>();

        public QE qe = new QE();

        public Discussions discussions = new Discussions();
    }

    public static class TriageRule {

        public String id;

        public String title;

        public String body;

        public String titleBody;

        public String expression;

        /**
         * @deprecated use files instead
         */
        @JsonDeserialize(as = TreeSet.class)
        @Deprecated(forRemoval = true)
        public Set<String> directories = new TreeSet<>();

        @JsonDeserialize(as = TreeSet.class)
        public Set<String> files = new TreeSet<>();

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

        @JsonDeserialize(as = HashSet.class)
        public Set<String> ignoredFlakyTests = new HashSet<>();
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
        @JsonDeserialize(as = TreeSet.class)
        public Set<String> files = new TreeSet<>();

        public UserRule users;

    }

    public static class UserRule {
        public Integer minContributions;
    }

    public static class Develocity {

        public boolean enabled = false;

        public String url;
    }

    public static class GuardedBranch {

        public String ref;

        @JsonDeserialize(as = TreeSet.class)
        public Set<String> notify = new TreeSet<>();
    }

    boolean isFeatureEnabled(Feature feature) {
        return features.contains(Feature.ALL) || features.contains(feature);
    }
}
