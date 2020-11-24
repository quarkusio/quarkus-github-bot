package io.quarkus.bot;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

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
import io.quarkus.bot.util.Strings;

class TriageIssue {

    private static final Logger LOG = Logger.getLogger(TriageIssue.class);

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void triageIssue(@Issue.Opened GHEventPayload.Issue issuePayload,
            @ConfigFile("quarkus-bot-java.yml") QuarkusBotConfigFile quarkusBotConfigFile) throws IOException {

        if (quarkusBotConfigFile == null) {
            LOG.error("Unable to find triage configuration.");
            return;
        }

        GHIssue issue = issuePayload.getIssue();
        boolean triaged = false;
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
                triaged = true;
            }
        }

        if (!labels.isEmpty()) {
            if (!quarkusBotConfig.dryRun) {
                issue.addLabels(labels.toArray(new String[0]));
            } else {
                LOG.info("Issue #" + issue.getNumber() + " - Add labels: " + String.join(", ", labels));
            }
        }

        if (!mentions.isEmpty()) {
            if (!quarkusBotConfig.dryRun) {
                issue.comment("/cc @" + String.join(", @", mentions));
            } else {
                LOG.info("Issue #" + issue.getNumber() + " - Mentions: " + String.join(", ", mentions));
            }
        }

        if (!triaged && !GHIssues.hasAreaLabel(issue)) {
            if (!quarkusBotConfig.dryRun) {
                issue.addLabels(Labels.TRIAGE_NEEDS_TRIAGE);
            } else {
                LOG.info("Issue #" + issue.getNumber() + " - Add label: " + Labels.TRIAGE_NEEDS_TRIAGE);
            }
        }
    }

    private static boolean matchRule(GHIssue issue, TriageRule rule) {
        try {
            if (Strings.isNotBlank(rule.title)) {
                if (Pattern.compile(rule.title, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(issue.getTitle())
                        .matches()) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error evaluating regular expression: " + rule.title, e);
        }

        try {
            if (Strings.isNotBlank(rule.body)) {
                if (Pattern.compile(rule.body, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(issue.getBody()).matches()) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error evaluating regular expression: " + rule.body, e);
        }

        try {
            if (Strings.isNotBlank(rule.titleBody)) {
                if (Pattern.compile(rule.titleBody, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(issue.getTitle())
                        .matches() ||
                        Pattern.compile(rule.titleBody, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(issue.getBody())
                                .matches()) {
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
