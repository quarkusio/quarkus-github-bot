package io.quarkus.bot.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus-github-bot")
public interface QuarkusGitHubBotConfig {

    Optional<Boolean> dryRun();

    public default boolean isDryRun() {
        Optional<Boolean> dryRun = dryRun();
        return dryRun.isPresent() && dryRun.get();
    }
}
