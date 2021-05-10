package io.quarkus.bot.config;

import java.util.Optional;

import io.quarkus.arc.config.ConfigProperties;

@ConfigProperties
public class QuarkusBotConfig {

    Optional<Boolean> dryRun;

    Optional<String> accessToken;

    public void setDryRun(Optional<Boolean> dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isDryRun() {
        return dryRun.isPresent() && dryRun.get();
    }

    public void setAccessToken(Optional<String> accessToken) {
        this.accessToken = accessToken;
    }

    public Optional<String> getAccessToken() {
        return accessToken;
    }
}
