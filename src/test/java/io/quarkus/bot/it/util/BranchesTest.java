package io.quarkus.bot.it.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.bot.util.Branches;

public class BranchesTest {

    @Test
    public void testIsVersionBranch() {
        assertThat(Branches.isVersionBranch("3.8")).isTrue();
        assertThat(Branches.isVersionBranch("3.10")).isTrue();
        assertThat(Branches.isVersionBranch("10.10")).isTrue();
        assertThat(Branches.isVersionBranch("2.5")).isTrue();
        assertThat(Branches.isVersionBranch("4.0")).isTrue();
        assertThat(Branches.isVersionBranch("main")).isFalse();
        assertThat(Branches.isVersionBranch("feat-4.0")).isFalse();
    }
}
