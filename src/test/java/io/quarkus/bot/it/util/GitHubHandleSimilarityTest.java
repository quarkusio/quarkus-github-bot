package io.quarkus.bot.it.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import io.quarkus.bot.util.GitHubHandleSimilarity;

public class GitHubHandleSimilarityTest {

    private static final Set<String> TEAM_MEMBERS = new TreeSet<>(
            Set.of("gsmet", "maxandersen", "cescoffier", "geoand", "gastaldi", "mkouba", "dmlloyd"));

    // --- Exact matches should NOT trigger ---

    @Test
    void exactMatchIsNotFlagged() {
        for (String member : TEAM_MEMBERS) {
            assertThat(GitHubHandleSimilarity.findSimilarTeamMember(member, TEAM_MEMBERS))
                    .as("Exact match for " + member)
                    .isEmpty();
        }
    }

    @Test
    void exactMatchCaseInsensitive() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("GSMET", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("Gsmet", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("MaxAndersen", TEAM_MEMBERS)).isEmpty();
    }

    // --- Distance 1: should trigger ---

    @Test
    void transposition() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("gsemt", TEAM_MEMBERS)).hasValue("gsmet");
    }

    @Test
    void insertion() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("gsmett", TEAM_MEMBERS)).hasValue("gsmet");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("ggsmet", TEAM_MEMBERS)).hasValue("gsmet");
    }

    @Test
    void deletion() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("gsme", TEAM_MEMBERS)).hasValue("gsmet");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("gmet", TEAM_MEMBERS)).hasValue("gsmet");
    }

    @Test
    void substitution() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("gsmat", TEAM_MEMBERS)).hasValue("gsmet");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("xsmet", TEAM_MEMBERS)).hasValue("gsmet");
    }

    // --- Distance 2: should trigger for handles with 6+ chars ---

    @Test
    void nearMaxandersen() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("maxanderson", TEAM_MEMBERS)).hasValue("maxandersen");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("maxandresen", TEAM_MEMBERS)).hasValue("maxandersen");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("maxandersen1", TEAM_MEMBERS)).hasValue("maxandersen");
    }

    @Test
    void nearCescoffier() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("cescofier", TEAM_MEMBERS)).hasValue("cescoffier");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("cescoffir", TEAM_MEMBERS)).hasValue("cescoffier");
    }

    @Test
    void nearGeoand() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("geand", TEAM_MEMBERS)).hasValue("geoand");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("goand", TEAM_MEMBERS)).hasValue("geoand");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("geoandd", TEAM_MEMBERS)).hasValue("geoand");
    }

    @Test
    void nearGastaldi() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("gastaaldi", TEAM_MEMBERS)).hasValue("gastaldi");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("gastldi", TEAM_MEMBERS)).hasValue("gastaldi");
    }

    @Test
    void nearMkouba() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("mkoba", TEAM_MEMBERS)).hasValue("mkouba");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("mkoubba", TEAM_MEMBERS)).hasValue("mkouba");
    }

    @Test
    void nearDmlloyd() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("dmloyd", TEAM_MEMBERS)).hasValue("dmlloyd");
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("dmlloyed", TEAM_MEMBERS)).hasValue("dmlloyd");
    }

    // --- Distance 2 from short handle (gsmet, 5 chars): should NOT trigger (threshold 1) ---

    @Test
    void distance2FromShortHandleNotFlagged() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("gsmat1", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("gamett", TEAM_MEMBERS)).isEmpty();
    }

    // --- Completely unrelated handles: should NOT trigger ---

    @Test
    void unrelatedHandles() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("johndoe", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("quarkusfan42", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("alice", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("bob", TEAM_MEMBERS)).isEmpty();
    }

    // --- Known Quarkus contributors: false positive check ---

    @Test
    void knownContributorsNotFlagged() {
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("sberyozkin", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("FroMage", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("yrodiere", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("Sanne", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("holly-cummins", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("stuartsquare", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("jmartisk", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("aloubyansky", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("machi1990", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("ozangunalp", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("radcortez", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("ia3andy", TEAM_MEMBERS)).isEmpty();
        assertThat(GitHubHandleSimilarity.findSimilarTeamMember("famod", TEAM_MEMBERS)).isEmpty();
    }

    // --- No cross-team false positives ---

    @Test
    void teamMembersDoNotTriggerAgainstEachOther() {
        for (String member : TEAM_MEMBERS) {
            assertThat(GitHubHandleSimilarity.findSimilarTeamMember(member, TEAM_MEMBERS))
                    .as("Team member " + member + " should not trigger against the team")
                    .isEmpty();
        }
    }

}
