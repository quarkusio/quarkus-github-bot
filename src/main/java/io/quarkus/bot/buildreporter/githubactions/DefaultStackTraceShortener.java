package io.quarkus.bot.buildreporter.githubactions;

import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;

import io.quarkus.arc.DefaultBean;

@Singleton
@DefaultBean
public final class DefaultStackTraceShortener implements StackTraceShortener {

    @Override
    public String shorten(String stacktrace, int length) {
        if (StringUtils.isBlank(stacktrace)) {
            return null;
        }

        return StringUtils.abbreviate(stacktrace, length);
    }
}
