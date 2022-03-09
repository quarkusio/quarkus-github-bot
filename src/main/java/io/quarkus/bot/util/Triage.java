package io.quarkus.bot.util;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.jboss.logging.Logger;

import io.quarkus.bot.config.QuarkusGitHubBotConfigFile.TriageRule;
import io.quarkus.bot.el.SimpleELContext;

public final class Triage {

    private static final Logger LOG = Logger.getLogger(Triage.class);

    private Triage() {
    }

    public static boolean matchRule(String title, String body, TriageRule rule) {
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
}
