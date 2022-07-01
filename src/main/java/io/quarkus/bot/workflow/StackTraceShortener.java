package io.quarkus.bot.workflow;

import java.util.stream.Collectors;

public interface StackTraceShortener {

    String shorten(String stacktrace, int length);

    default String shorten(String stacktrace, int length, int maxLines) {
        String shortenedStacktrace = shorten(stacktrace, length);

        if (shortenedStacktrace == null || shortenedStacktrace.isBlank()) {
            return null;
        }

        return shortenedStacktrace.lines().limit(maxLines).collect(Collectors.joining("\n"));
    }
}
