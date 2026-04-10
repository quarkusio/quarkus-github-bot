package io.quarkus.bot.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;

@Singleton
public class TeamMemberService {

    private static final Logger LOG = Logger.getLogger(TeamMemberService.class);

    @CacheResult(cacheName = "team-members-cache")
    public Set<String> getTeamMembers(GitHub gitHub, @CacheKey String org, Set<String> teamSlugs) {
        Set<String> members = new TreeSet<>();

        try {
            GHOrganization ghOrg = gitHub.getOrganization(org);

            for (String teamSlug : teamSlugs) {
                try {
                    GHTeam team = ghOrg.getTeamBySlug(teamSlug);
                    if (team == null) {
                        LOG.warn("Team " + teamSlug + " not found in organization " + org);
                        continue;
                    }
                    for (GHUser member : team.getMembers()) {
                        members.add(member.getLogin().toLowerCase(Locale.ROOT));
                    }
                } catch (IOException e) {
                    LOG.error("Error fetching members for team " + teamSlug + " in organization " + org, e);
                }
            }
        } catch (IOException e) {
            LOG.error("Error fetching organization " + org, e);
        }

        return members;
    }
}
