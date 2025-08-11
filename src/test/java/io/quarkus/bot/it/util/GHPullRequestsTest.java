package io.quarkus.bot.it.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.bot.util.GHPullRequests;

public class GHPullRequestsTest {
    public static final String FEATURES_CHECK_EDITORIAL_RULES = "features: [ CHECK_EDITORIAL_RULES ]\n";

    @Test
    public void testDropVersionSuffix() {
        assertThat(GHPullRequests.dropVersionSuffix("My PR", "3.8")).isEqualTo(("My PR"));
        assertThat(GHPullRequests.dropVersionSuffix("My PR", "main")).isEqualTo(("My PR"));
        assertThat(GHPullRequests.dropVersionSuffix("(3.8) My PR", "main")).isEqualTo(("(3.8) My PR"));
        assertThat(GHPullRequests.dropVersionSuffix("[3.8] My PR", "main")).isEqualTo(("[3.8] My PR"));
        assertThat(GHPullRequests.dropVersionSuffix("[3.8] My PR", "3.8")).isEqualTo(("My PR"));
        assertThat(GHPullRequests.dropVersionSuffix("[3.9] My PR", "3.8")).isEqualTo(("My PR"));
        assertThat(GHPullRequests.dropVersionSuffix("(3.9) My PR", "3.8")).isEqualTo(("My PR"));
        assertThat(GHPullRequests.dropVersionSuffix("My PR [3.7]", "3.8")).isEqualTo(("My PR [3.7]"));
        assertThat(GHPullRequests.dropVersionSuffix("3.10.4 Backports 1", "3.10")).isEqualTo(("3.10.4 Backports 1"));
        assertThat(GHPullRequests.dropVersionSuffix("(3.10) My PR", "3.10")).isEqualTo(("My PR"));
        assertThat(GHPullRequests.dropVersionSuffix("[3.10] My PR", "3.10")).isEqualTo(("My PR"));
    }

    @Test
    public void testNormalizeTitle() {
        assertThat(GHPullRequests.normalizeTitle("My PR", "3.8")).isEqualTo(("[3.8] My PR"));
        assertThat(GHPullRequests.normalizeTitle("My PR", "3.10")).isEqualTo(("[3.10] My PR"));
        assertThat(GHPullRequests.normalizeTitle("My PR", "main")).isEqualTo(("My PR"));
        assertThat(GHPullRequests.normalizeTitle("(3.8) My PR", "main")).isEqualTo(("(3.8) My PR"));
        assertThat(GHPullRequests.normalizeTitle("3.8.4 backports 1", "3.8")).isEqualTo(("[3.8] 3.8.4 backports 1"));
        assertThat(GHPullRequests.normalizeTitle("(3.10) My PR", "3.10")).isEqualTo(("[3.10] My PR"));
        assertThat(GHPullRequests.normalizeTitle("[3.8] My PR", "main")).isEqualTo(("[3.8] My PR"));
        assertThat(GHPullRequests.normalizeTitle("[3.8] My PR", "3.8")).isEqualTo(("[3.8] My PR"));
        assertThat(GHPullRequests.normalizeTitle("[3.9] My PR", "3.8")).isEqualTo(("[3.8] My PR"));
        assertThat(GHPullRequests.normalizeTitle("(3.9) My PR", "3.8")).isEqualTo(("[3.8] My PR"));
        assertThat(GHPullRequests.normalizeTitle("My PR [3.7]", "3.8")).isEqualTo(("[3.8] My PR [3.7]"));
        assertThat(GHPullRequests.normalizeTitle("2.10 - My PR", "2.10")).isEqualTo(("[2.10] My PR"));
        assertThat(GHPullRequests.normalizeTitle("3.10.4 Backports 1", "3.10")).isEqualTo(("[3.10] 3.10.4 Backports 1"));
    }
}
