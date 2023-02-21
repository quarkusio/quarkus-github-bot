package io.quarkus.bot.util;

import io.quarkus.bot.config.QuarkusGitHubBotConfigFile.TriageRule;
import io.quarkus.bot.el.SimpleELContext;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHPullRequest;

import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;

public final class Triage {

    private static final Logger LOG = Logger.getLogger(Triage.class);

    private Triage() {
    }

    public static boolean matchRuleFromDescription(String title, String body, TriageRule rule) {
        try {
            if (Strings.isNotBlank(rule.title)) {
                if (Patterns.find(rule.title, title)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error evaluating regular expression: " + rule.title, e);
        }

        try {
            if (Strings.isNotBlank(rule.body)) {
                if (Patterns.find(rule.body, body)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("Error evaluating regular expression: " + rule.body, e);
        }

        try {
            if (Strings.isNotBlank(rule.titleBody)) {
                if (Patterns.find(rule.titleBody, title) || Patterns.find(rule.titleBody, body)) {
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
                        expressionFactory.createValueExpression(title, String.class));
                context.getVariableMapper().setVariable("body",
                        expressionFactory.createValueExpression(body, String.class));
                context.getVariableMapper().setVariable("titleBody",
                        expressionFactory.createValueExpression(title + "\n\n" + body, String.class));

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

    public static boolean matchRuleFromChangedFiles(GHPullRequest pullRequest, TriageRule rule) {
        // for now, we only use the files but we could also use the other rules at some point
        if (rule.directories.isEmpty() && rule.files.isEmpty()) {
            return false;
        }

        PullRequestFilesMatcher prMatcher = new PullRequestFilesMatcher(pullRequest);
        if (prMatcher.changedFilesMatch(rule.files)) {
            return true;
        }
        if (prMatcher.changedFilesMatch(rule.directories)) {
            return true;
        }

        return false;
    }
}
