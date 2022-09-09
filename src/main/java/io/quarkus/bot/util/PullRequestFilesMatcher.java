package io.quarkus.bot.util;

import com.hrakaroo.glob.GlobPattern;
import com.hrakaroo.glob.MatchingEngine;
import io.quarkus.cache.CacheResult;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.PagedIterable;

import java.util.Collection;

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

    @CacheResult(cacheName = "glob-cache")
    MatchingEngine compileGlob(String filenamePattern) {
        return GlobPattern.compile(filenamePattern);
    }
}
