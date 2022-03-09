package io.quarkus.bot;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;

import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.util.IssueExtractor;
import io.quarkus.bot.util.Labels;
import io.quarkus.bot.util.Strings;

class AffectKindToPullRequest {

    private static final Logger LOG = Logger.getLogger(AffectKindToPullRequest.class);

    private static final String DEPENDABOT = "dependabot";

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void dependabotComponentUpgrade(@PullRequest.Closed GHEventPayload.PullRequest pullRequestPayload) throws IOException {
        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();

        if (!pullRequest.isMerged()) {
            return;
        }

        if (pullRequest.getUser() == null || pullRequest.getUser().getLogin() == null
                || !pullRequest.getUser().getLogin().startsWith(DEPENDABOT)) {
            return;
        }

        if (!quarkusBotConfig.isDryRun()) {
            pullRequest.addLabels(Labels.KIND_COMPONENT_UPGRADE);
        } else {
            LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add label: " + Labels.KIND_COMPONENT_UPGRADE);
        }
    }

    void fromIssues(@PullRequest.Closed GHEventPayload.PullRequest pullRequestPayload) throws IOException {
        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();

        if (!pullRequest.isMerged() || Strings.isBlank(pullRequest.getBody())) {
            return;
        }

        IssueExtractor issueExtractor = new IssueExtractor(pullRequest.getRepository().getFullName());
        Set<Integer> issueNumbers = issueExtractor.extractIssueNumbers(pullRequest.getBody());

        Set<String> labels = new HashSet<>();
        for (Integer issueNumber : issueNumbers) {
            try {
                pullRequest.getRepository().getIssue(issueNumber).getLabels().stream()
                        .map(l -> l.getName())
                        .filter(l -> Labels.KIND_LABELS.contains(l))
                        .map(AffectKindToPullRequest::mapLabel)
                        .forEach(l -> labels.add(l));
            } catch (Exception e) {
                // ignoring
            }
        }

        if (!labels.isEmpty()) {
            if (!quarkusBotConfig.isDryRun()) {
                pullRequest.addLabels(labels.toArray(new String[0]));
            } else {
                LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add labels: " + labels);
            }
        }
    }

    private static String mapLabel(String originalLabel) {
        if (Labels.KIND_BUG.equals(originalLabel)) {
            return Labels.KIND_BUGFIX;
        }

        return originalLabel;
    }
}
