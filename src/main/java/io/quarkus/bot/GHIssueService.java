package io.quarkus.bot;

import java.io.IOException;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHIssue;

@Singleton
public class GHIssueService {

    private static final Logger LOG = Logger.getLogger(GHIssueService.class);

    public void setIssueTitle(GHIssue issue, String newTitle, boolean isDryRun) {
        if (!isDryRun) {
            try {
                issue.setTitle(newTitle);
                LOG.debugf("Pull request #%d - Updated title to: %s", issue.getNumber(), newTitle);
            } catch (IOException e) {
                LOG.errorf(e, "Pull Request #%d - Failed to update title", issue.getNumber());
            }
        } else {
            LOG.infof("Pull request #%d - Update title to (dry-run): %s", issue.getNumber(), newTitle);
        }
    }

}
