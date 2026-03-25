package io.quarkus.bot.it.support;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Test profile enabling dry-run mode for retest command integration tests.
 */
public class DryRunTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus-github-bot.dry-run", "true");
    }
}
