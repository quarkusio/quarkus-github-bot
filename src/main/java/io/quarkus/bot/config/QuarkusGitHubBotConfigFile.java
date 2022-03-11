package io.quarkus.bot.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class QuarkusGitHubBotConfigFile {

    @JsonDeserialize(as = HashSet.class)
    Set<Feature> features = new HashSet<>();

    public TriageConfig triage = new TriageConfig();

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

    boolean isFeatureEnabled(Feature feature) {
        return features.contains(Feature.ALL) || features.contains(feature);
    }
}
