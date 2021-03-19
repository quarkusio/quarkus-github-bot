package io.quarkus.bot;

import java.io.IOException;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.bot.config.QuarkusBotConfig;
import io.quarkus.bot.util.Labels;

public class RemoveNeedsTriageLabelFromClosedIssue {

    private static final Logger LOG = Logger.getLogger(RemoveNeedsTriageLabelFromClosedIssue.class);

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    void onClose(@Issue.Closed GHEventPayload.Issue issuePayload) throws IOException {
        GHIssue issue = issuePayload.getIssue();
        for (GHLabel label : issue.getLabels()) {
            if (label.getName().equals(Labels.TRIAGE_NEEDS_TRIAGE)) {
                if (!quarkusBotConfig.isDryRun()) {
                    issue.removeLabels(Labels.TRIAGE_NEEDS_TRIAGE);
                } else {
                    LOG.info("Issue #" + issue.getNumber() + " - Remove label: " + Labels.TRIAGE_NEEDS_TRIAGE);
                }
                break;
            }
        }
    }
}
