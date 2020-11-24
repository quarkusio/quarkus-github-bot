package io.quarkus.bot.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class QuarkusBotConfigFile {

    public TriageConfig triage;

    public static class TriageConfig {

        public List<TriageRule> rules = new ArrayList<>();
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

        public boolean notifyInPullRequest;
    }
}
