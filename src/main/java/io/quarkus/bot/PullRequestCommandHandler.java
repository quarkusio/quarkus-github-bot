package io.quarkus.bot;

import io.quarkiverse.githubapp.event.IssueComment;
import io.quarkus.bot.command.Command;
import io.quarkus.bot.config.QuarkusBotConfig;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.ReactionContent;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PullRequestCommandHandler {

    private static final Logger LOG = Logger.getLogger(PullRequestCommandHandler.class);

    private static final String QUARKUS_BOT_NAME = "quarkus-bot[bot]";
    private static final Pattern QUARKUS_BOT_MENTION = Pattern.compile("^@(?:quarkus-?)?bot\\s+([a-z _\\-]+)");

    @Inject
    Instance<Command<GHPullRequest>> commands;

    @Inject
    QuarkusBotConfig quarkusBotConfig;

    public void onComment(@IssueComment.Created @IssueComment.Edited GHEventPayload.IssueComment commentPayload)
            throws IOException {
        GHUser user = commentPayload.getComment().getUser();
        GHIssue issue = commentPayload.getIssue();
        GHRepository repository = commentPayload.getRepository();

        if (QUARKUS_BOT_NAME.equals(commentPayload.getComment().getUserName())) {
            return;
        }

        if (issue.isPullRequest()) {
            Optional<Command<GHPullRequest>> command = extractCommand(commentPayload.getComment().getBody());
            if (command.isPresent() && canRunCommand(repository, user)) {
                GHPullRequest pullRequest = repository.getPullRequest(issue.getNumber());
                ReactionContent reactionResult = command.get().run(pullRequest);
                postReaction(commentPayload, issue, reactionResult);
            } else {
                postReaction(commentPayload, issue, ReactionContent.MINUS_ONE);
            }
        }
    }

    private void postReaction(GHEventPayload.IssueComment comment, GHIssue issue, ReactionContent reactionResult)
            throws IOException {
        if (!quarkusBotConfig.isDryRun()) {
            comment.getComment().createReaction(reactionResult);
        } else {
            LOG.info("Pull Request #" + issue.getNumber() + " - Add reaction: " + reactionResult.getContent());
        }
    }

    private Optional<Command<GHPullRequest>> extractCommand(String comment) {
        Matcher matcher = QUARKUS_BOT_MENTION.matcher(comment);
        if (matcher.matches()) {
            String commandLabel = matcher.group(1);
            return commands.stream().filter(command -> command.labels().contains(commandLabel)).findFirst();
        }
        return Optional.empty();
    }

    private boolean canRunCommand(GHRepository repository, GHUser user) throws IOException {
        return repository.getPermission(user) == GHPermissionType.WRITE
                || repository.getPermission(user) == GHPermissionType.ADMIN;
    }
}
