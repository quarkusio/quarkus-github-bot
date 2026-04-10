package io.quarkus.bot.util;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class GitHubHandleSimilarity {

    private GitHubHandleSimilarity() {
    }

    /**
     * Finds a team member whose handle is suspiciously similar to the given handle.
     * Returns empty if the handle is an exact match (the person IS the team member)
     * or if no team member is close enough.
     * <p>
     * Team members are expected to be already lowercased.
     */
    public static Optional<String> findSimilarTeamMember(String handle, Set<String> teamMembers) {
        String lowerHandle = handle.toLowerCase(Locale.ROOT);

        for (String member : teamMembers) {
            if (lowerHandle.equals(member)) {
                return Optional.empty();
            }

            int threshold = member.length() < 6 ? 1 : 2;
            int distance = damerauLevenshteinDistance(lowerHandle, member, threshold);

            if (distance <= threshold) {
                return Optional.of(member);
            }
        }

        return Optional.empty();
    }

    /**
     * Computes the Damerau-Levenshtein distance between two strings,
     * with an early exit if the distance exceeds the given threshold.
     * Supports insertions, deletions, substitutions, and adjacent transpositions.
     */
    static int damerauLevenshteinDistance(String source, String target, int threshold) {
        int sourceLength = source.length();
        int targetLength = target.length();

        if (Math.abs(sourceLength - targetLength) > threshold) {
            return threshold + 1;
        }

        int[][] distance = new int[sourceLength + 1][targetLength + 1];

        for (int i = 0; i <= sourceLength; i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= targetLength; j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= sourceLength; i++) {
            for (int j = 1; j <= targetLength; j++) {
                int cost = source.charAt(i - 1) == target.charAt(j - 1) ? 0 : 1;

                distance[i][j] = Math.min(
                        Math.min(
                                distance[i - 1][j] + 1,
                                distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + cost);

                if (i > 1 && j > 1
                        && source.charAt(i - 1) == target.charAt(j - 2)
                        && source.charAt(i - 2) == target.charAt(j - 1)) {
                    distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + cost);
                }
            }
        }

        return distance[sourceLength][targetLength];
    }
}
