package io.quarkus.bot.config;

import java.util.Optional;

import io.quarkus.arc.config.ConfigProperties;

@ConfigProperties
public class QuarkusBotConfig {

    Optional<Boolean> dryRun;

    public void setDryRun(Optional<Boolean> dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isDryRun() {
        return dryRun.isPresent() && dryRun.get();
    }
}
