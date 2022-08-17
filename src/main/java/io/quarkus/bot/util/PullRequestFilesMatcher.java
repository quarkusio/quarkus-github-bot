package io.quarkus.bot.util;

import com.hrakaroo.glob.GlobPattern;
import com.hrakaroo.glob.MatchingEngine;
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

    public boolean changedFilesMatchDirectory(Collection<String> directories) {
        for (GHPullRequestFileDetail changedFile : pullRequest.listFiles()) {
            for (String directory : directories) {

                if (!directory.contains("*")) {
                    if (changedFile.getFilename().startsWith(directory)) {
                        return true;
                    }
                } else {
                    try {
                        MatchingEngine matchingEngine = GlobPattern.compile(directory);
                        if (matchingEngine.matches(changedFile.getFilename())) {
                            return true;
                        }
                    } catch (Exception e) {
                        LOG.error("Error evaluating glob expression: " + directory, e);
                    }
                }
            }
        }
        return false;
    }

    public boolean changedFilesMatchFile(Collection<String> files) {

        PagedIterable<GHPullRequestFileDetail> prFiles = pullRequest.listFiles();

        if (prFiles == null || files == null) {
            return false;
        }

        for (GHPullRequestFileDetail changedFile : prFiles) {
            for (String file : files) {

                if (!file.contains("*")) {
                    if (changedFile.getFilename().endsWith(file)) {
                        return true;
                    }
                } else {
                    try {
                        MatchingEngine matchingEngine = GlobPattern.compile(file);
                        if (matchingEngine.matches(changedFile.getFilename())) {
                            return true;
                        }
                    } catch (Exception e) {
                        LOG.error("Error evaluating glob expression: " + file, e);
                    }
                }
            }
        }
        return false;
    }
}
