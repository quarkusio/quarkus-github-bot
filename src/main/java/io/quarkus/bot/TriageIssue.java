package io.quarkus.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;
import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.config.QuarkusBotConfigFile;
import io.quarkus.bot.config.QuarkusBotConfigFile.TriageRule;
import io.quarkus.bot.el.SimpleELContext;
import io.quarkus.bot.util.GHIssues;
import io.quarkus.bot.util.Labels;
import io.quarkus.bot.util.Patterns;
import io.quarkus.bot.util.Strings;

class TriageIssue {

    private static final Logger LOG = Logger.getLogger(TriageIssue.class);

    /**
     * We cannot add more than 100 labels and we have some other automatic labels such as kind/bug.
     */
    private static final int LABEL_SIZE_LIMIT = 95;

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void triageIssue(@Issue.Opened GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-bot.yml") QuarkusBotConfigFile quarkusBotConfigFile) throws IOException {

        if (quarkusBotConfigFile == null) {
            LOG.error("Unable to find triage configuration.");
            return;
        }

        GHIssue issue = issuePayload.getIssue();
        Set<String> labels = new TreeSet<>();
        Set<String> mentions = new TreeSet<>();

        for (TriageRule rule : quarkusBotConfigFile.triage.rules) {
            if (matchRule(issue, rule)) {
                if (!rule.labels.isEmpty()) {
                    labels.addAll(rule.labels);
                }
                if (!rule.notify.isEmpty()) {
                    for (String mention : rule.notify) {
                        if (!mention.equals(issue.getUser().getLogin())) {
                            mentions.add(mention);
                        }
                    }
                }
            }
        }

        if (!labels.isEmpty()) {
            if (!quarkusBotConfig.isDryRun()) {
                issue.addLabels(limit(labels).toArray(new String[0]));
            } else {
                LOG.info("Issue #" + issue.getNumber() + " - Add labels: " + String.join(", ", limit(labels)));
            }
        }

        if (!mentions.isEmpty()) {
            if (!quarkusBotConfig.isDryRun()) {
                issue.comment("/cc @" + String.join(", @", mentions));
            } else {
                LOG.info("Issue #" + issue.getNumber() + " - Mentions: " + String.join(", ", mentions));
            }
        }

        if (mentions.isEmpty() && !hasAreaLabels(labels) && !GHIssues.hasAreaLabel(issue)) {
            if (!quarkusBotConfig.isDryRun()) {
                issue.addLabels(Labels.TRIAGE_NEEDS_TRIAGE);
            } else {
                LOG.info("Issue #" + issue.getNumber() + " - Add label: " + Labels.TRIAGE_NEEDS_TRIAGE);
            }
        }
    }

    private boolean hasAreaLabels(Set<String> labels) {
        for (String label : labels) {
            if (label.startsWith(Labels.AREA_PREFIX)) {
                return true;
            }
        }

        return false;
    }

    private static Collection<String> limit(Set<String> labels) {
        if (labels.size() <= LABEL_SIZE_LIMIT) {
            return labels;
        }

        return new ArrayList<>(labels).subList(0, LABEL_SIZE_LIMIT);
    }

    private static boolean matchRule(GHIssue issue, TriageRule rule) {
        try {
            if (Strings.isNotBlank(rule.title)) {
                if (Patterns.find(rule.title, issue.getTitle())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error evaluating regular expression: " + rule.title, e);
        }

        try {
            if (Strings.isNotBlank(rule.body)) {
                if (Patterns.find(rule.body, issue.getBody())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error evaluating regular expression: " + rule.body, e);
        }

        try {
            if (Strings.isNotBlank(rule.titleBody)) {
                if (Patterns.find(rule.titleBody, issue.getTitle()) || Patterns.find(rule.titleBody, issue.getBody())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error evaluating regular expression: " + rule.titleBody, e);
        }

        try {
            if (Strings.isNotBlank(rule.expression)) {
                String expression = "${" + rule.expression + "}";

                ExpressionFactory expressionFactory = ELManager.getExpressionFactory();

                ELContext context = new SimpleELContext(expressionFactory);
                context.getVariableMapper().setVariable("title",
                        expressionFactory.createValueExpression(issue.getTitle(), String.class));
                context.getVariableMapper().setVariable("body",
                        expressionFactory.createValueExpression(issue.getBody(), String.class));
                context.getVariableMapper().setVariable("titleBody",
                        expressionFactory.createValueExpression(issue.getTitle() + "\n\n" + issue.getBody(), String.class));

                ValueExpression valueExpression = expressionFactory.createValueExpression(context, expression, Boolean.class);

                Boolean value = (Boolean) valueExpression.getValue(context);
                if (Boolean.TRUE.equals(value)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error evaluating expression: " + rule.expression, e);
        }

        return false;
    }
}
