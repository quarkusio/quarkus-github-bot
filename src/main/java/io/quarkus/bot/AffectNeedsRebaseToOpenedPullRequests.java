package io.quarkus.bot;

import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkiverse.githubapp.event.Push;
import io.quarkus.bot.util.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.io.IOException;

public class AffectNeedsRebaseToOpenedPullRequests {

    /**
     * Affect triage/needs-rebase label on pull request events
     */
    void triageNeedsRebaseOnPullRequest(
            @PullRequest.Opened @PullRequest.Reopened @PullRequest.Synchronize GHEventPayload.PullRequest pullRequestPayload)
            throws IOException {
        affectNeedsRebaseLabel(pullRequestPayload.getPullRequest());
    }

    void triageNeedsRebaseOnRepositoryPush(@Push GHEventPayload.Push pushRequestPayload) throws IOException {
        GHRepository repository = pushRequestPayload.getRepository();
        String defaultBranch = repository.getDefaultBranch();
        for (GHPullRequest pullRequest : repository.getPullRequests(GHIssueState.OPEN)) {
            if (!defaultBranch.equals(pullRequest.getBase().getRef())) {
                continue;
            }
            affectNeedsRebaseLabel(pullRequest);
        }
    }

    private void affectNeedsRebaseLabel(GHPullRequest pullRequest) throws IOException {
        Boolean mergeable = pullRequest.getMergeable();
        boolean hasNeedsRebaseLabel = pullRequest.getLabels().stream()
                .anyMatch(label -> Labels.TRIAGE_NEEDS_REBASE.equals(label.getName()));
        if (mergeable != null) {
            if (mergeable) {
                if (hasNeedsRebaseLabel) {
                    pullRequest.removeLabels(Labels.TRIAGE_NEEDS_REBASE);
                }
            } else {
                if (!hasNeedsRebaseLabel) {
                    pullRequest.addLabels(Labels.TRIAGE_NEEDS_REBASE);
                }
            }
        }
    }
}
