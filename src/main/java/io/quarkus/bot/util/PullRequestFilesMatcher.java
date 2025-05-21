package io.quarkus.bot.util;

import java.util.Collection;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.PagedIterable;

import com.hrakaroo.glob.GlobPattern;
import com.hrakaroo.glob.MatchingEngine;

import io.quarkus.cache.CacheResult;

public class PullRequestFilesMatcher {

    private static final Logger LOG = Logger.getLogger(PullRequestFilesMatcher.class);

    private final GHPullRequest pullRequest;

    public PullRequestFilesMatcher(GHPullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }

    public boolean changedFilesMatch(Collection<String> filenamePatterns) {
        if (filenamePatterns.isEmpty()) {
            return false;
        }

        PagedIterable<GHPullRequestFileDetail> prFiles = pullRequest.listFiles();
        if (prFiles != null) {
            for (GHPullRequestFileDetail changedFile : prFiles) {
                for (String filenamePattern : filenamePatterns) {

                    if (!filenamePattern.contains("*")) {
                        if (changedFile.getFilename().startsWith(filenamePattern)) {
                            return true;
                        }
                    } else {
                        try {
                            MatchingEngine matchingEngine = compileGlob(filenamePattern);
                            if (matchingEngine.matches(changedFile.getFilename())) {
                                return true;
                            }
                        } catch (Exception e) {
                            LOG.error("Error evaluating glob expression: " + filenamePattern, e);
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean changedFilesOnlyMatch(Collection<String> filenamePatterns) {
        if (filenamePatterns.isEmpty()) {
            return false;
        }

        PagedIterable<GHPullRequestFileDetail> prFiles = pullRequest.listFiles();
        if (prFiles != null) {
            for (GHPullRequestFileDetail changedFile : prFiles) {
                if (filenamePatterns.stream().anyMatch(p -> matchFilenamePattern(p, changedFile.getFilename()))) {
                    continue;
                }

                return false;
            }
        }

        return true;
    }

    private boolean matchFilenamePattern(String filenamePattern, String changedFile) {
        if (!filenamePattern.contains("*")) {
            if (changedFile.startsWith(filenamePattern)) {
                return true;
            }
        } else {
            try {
                MatchingEngine matchingEngine = compileGlob(filenamePattern);
                if (matchingEngine.matches(changedFile)) {
                    return true;
                }
            } catch (Exception e) {
                LOG.error("Error evaluating glob expression: " + filenamePattern, e);
            }
        }

        return false;
    }

    @CacheResult(cacheName = "glob-cache")
    MatchingEngine compileGlob(String filenamePattern) {
        return GlobPattern.compile(filenamePattern);
    }
}
