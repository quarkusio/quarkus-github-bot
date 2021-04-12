package io.quarkus.bot.workflow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public final class StackTraceUtils {

    private static final String HTML_INTERNAL_ERROR_MARKER = "<title>Internal Server Error";
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile("Actual: <!doctype html>.*?<pre>(.*?)</pre>",
            Pattern.DOTALL);

    public static String abbreviate(String stacktrace, int length) {
        if (stacktrace.contains(HTML_INTERNAL_ERROR_MARKER)) {
            // this is an HTML error, let's get to the stacktrace
            Matcher matcher = STACK_TRACE_PATTERN.matcher(stacktrace);
            StringBuilder sb = new StringBuilder();
            if (matcher.find()) {
                matcher.appendReplacement(sb, "Actual: An Internal Server Error with stack trace:\n$1");
                stacktrace = sb.toString();
            }
        }

        return StringUtils.abbreviate(stacktrace, length);
    }

    private StackTraceUtils() {
    }
}
