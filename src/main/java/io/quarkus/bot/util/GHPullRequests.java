package io.quarkus.bot.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

public final class GHPullRequests {

    private static final Pattern CLEAN_VERSION_PATTERN = Pattern.compile("^\\[?\\(?[0-9]+\\.[0-9]+\\]?\\)?(?![\\.0-9])[ -]*");

    public static boolean hasLabel(GHPullRequest pullRequest, String labelName) {
        for (GHLabel label : pullRequest.getLabels()) {
            if (labelName.equals(label.getName())) {
                return true;
            }
        }
        return false;
    }

    public static List<GHPullRequest> matchingHeadPullRequests(GHRepository repository, GHRepository headRepository,
            String headRef, String headSha) throws IOException {
        if (repository == null || headRepository == null || headRef == null || headSha == null) {
            return List.of();
        }

        return matchingHeadPullRequests(repository.queryPullRequests()
                .head(headRepository.getOwnerName() + ":" + headRef)
                .list(), headRepository, headRef, headSha);
    }

    public static String dropVersionSuffix(String title, String branch) {
        if (title == null || title.isBlank()) {
            return title;
        }
        if (!Branches.isVersionBranch(branch)) {
            return title;
        }

        return CLEAN_VERSION_PATTERN.matcher(title).replaceFirst("");
    }

    public static String normalizeTitle(String title, String branch) {
        if (title == null || title.isBlank()) {
            return title;
        }
        if (!Branches.isVersionBranch(branch)) {
            return title;
        }

        return "[" + branch + "] " + dropVersionSuffix(title, branch);
    }

    private static List<GHPullRequest> matchingHeadPullRequests(Iterable<GHPullRequest> pullRequests,
            GHRepository headRepository, String headRef, String headSha) {
        List<GHPullRequest> matchingPullRequests = new ArrayList<>();

        for (GHPullRequest pullRequest : pullRequests) {
            if (!headRef.equals(pullRequest.getHead().getRef())) {
                continue;
            }
            if (!headSha.equals(pullRequest.getHead().getSha())) {
                continue;
            }
            if (!isSameRepository(headRepository, pullRequest.getHead().getRepository())) {
                continue;
            }

            matchingPullRequests.add(pullRequest);
        }

        return matchingPullRequests;
    }

    public static boolean isSameRepository(GHRepository left, GHRepository right) {
        if (left == null || right == null) {
            return false;
        }

        String leftFullName = left.getFullName();
        String rightFullName = right.getFullName();
        if (leftFullName != null && rightFullName != null) {
            return leftFullName.equals(rightFullName);
        }

        String leftOwner = left.getOwnerName();
        String rightOwner = right.getOwnerName();
        String leftName = left.getName();
        String rightName = right.getName();
        if (leftOwner == null || rightOwner == null || leftName == null || rightName == null) {
            return false;
        }

        return leftOwner.equals(rightOwner) && leftName.equals(rightName);
    }
}
