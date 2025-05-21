package io.quarkus.bot;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHCheckRun.AnnotationLevel;
import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHCheckRunBuilder.Annotation;
import org.kohsuke.github.GHCheckRunBuilder.Output;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.bot.config.Feature;
import io.quarkus.bot.config.QuarkusGitHubBotConfig;
import io.quarkus.bot.config.QuarkusGitHubBotConfigFile;

public class CheckPullRequestContributionRules {

    private static final Logger LOG = Logger.getLogger(CheckPullRequestContributionRules.class);

    public static final String FIXUP_COMMIT_PREFIX = "fixup!";

    public static final String MERGE_COMMIT_CHECK_RUN_NAME = "Check Pull Request - Merge commits";
    public static final String MERGE_COMMIT_ERROR_OUTPUT_TITLE = "PR contains merge commits";
    public static final String MERGE_COMMIT_ERROR_OUTPUT_SUMMARY = "Pull request that contains merge commits can not be merged";

    public static final String FIXUP_COMMIT_CHECK_RUN_NAME = "Check Pull Request - Fixup commits";
    public static final String FIXUP_COMMIT_ERROR_OUTPUT_TITLE = "PR contains fixup commits";
    public static final String FIXUP_COMMIT_ERROR_OUTPUT_SUMMARY = "Pull request that contains fixup commits can not be merged";

    public static final String ERROR_ANNOTATION_TITLE = "Error - Pull request commit check";
    public static final String ERROR_ANNOTATION_MSG = "[sha=%s ; message=\"%s\"]";

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void checkPullRequestContributionRules(
            @PullRequest.Opened @PullRequest.Reopened @PullRequest.Synchronize GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile) throws IOException {

        if (!Feature.CHECK_CONTRIBUTION_RULES.isEnabled(quarkusBotConfigFile)) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        CheckCommitData checkCommitData = getCheckCommitData(pullRequest);

        if (!quarkusBotConfig.isDryRun()) {

            GHRepository repostitory = pullRequest.getRepository();
            GHCommit headCommit = pullRequest.getHead().getCommit();

            // Merge commits
            buildCheckRun(checkCommitData.getMergeCommitDetails(), repostitory, headCommit,
                    MERGE_COMMIT_CHECK_RUN_NAME, MERGE_COMMIT_ERROR_OUTPUT_TITLE, MERGE_COMMIT_ERROR_OUTPUT_SUMMARY);

            // Fixup commits
            buildCheckRun(checkCommitData.getFixupCommitDetails(), repostitory, headCommit,
                    FIXUP_COMMIT_CHECK_RUN_NAME, FIXUP_COMMIT_ERROR_OUTPUT_TITLE, FIXUP_COMMIT_ERROR_OUTPUT_SUMMARY);
        } else {
            LOG.info("Pull request #" + pullRequest.getNumber());
            LOG.info(buildDryRunLogMessage(checkCommitData.getMergeCommitDetails(), MERGE_COMMIT_CHECK_RUN_NAME,
                    MERGE_COMMIT_ERROR_OUTPUT_SUMMARY));
            LOG.info(buildDryRunLogMessage(checkCommitData.getFixupCommitDetails(), FIXUP_COMMIT_CHECK_RUN_NAME,
                    FIXUP_COMMIT_ERROR_OUTPUT_SUMMARY));
        }
    }

    public static final class CheckCommitData {

        private List<GHPullRequestCommitDetail> mergeCommitDetails;
        private List<GHPullRequestCommitDetail> fixupCommitDetails;

        public CheckCommitData(List<GHPullRequestCommitDetail> mergeCommitDetails,
                List<GHPullRequestCommitDetail> fixupCommitDetails) {
            this.mergeCommitDetails = mergeCommitDetails;
            this.fixupCommitDetails = fixupCommitDetails;
        }

        public List<GHPullRequestCommitDetail> getMergeCommitDetails() {
            return mergeCommitDetails;
        }

        public List<GHPullRequestCommitDetail> getFixupCommitDetails() {
            return fixupCommitDetails;
        }
    }

    public static CheckCommitData getCheckCommitData(GHPullRequest pullRequest) {

        List<GHPullRequestCommitDetail> listMergeCommitDetail = new ArrayList<>();
        List<GHPullRequestCommitDetail> listFixupCommitDetail = new ArrayList<>();

        for (GHPullRequestCommitDetail commitDetail : pullRequest.listCommits()) {

            if (isMergeCommit(commitDetail)) {
                listMergeCommitDetail.add(commitDetail);
            }

            if (isFixupCommit(commitDetail)) {
                listFixupCommitDetail.add(commitDetail);
            }
        }

        return new CheckCommitData(listMergeCommitDetail, listFixupCommitDetail);
    }

    private static boolean isMergeCommit(GHPullRequestCommitDetail commitDetail) {
        return commitDetail.getParents().length > 1;
    }

    private static boolean isFixupCommit(GHPullRequestCommitDetail commitDetail) {
        GHPullRequestCommitDetail.Commit commit = commitDetail.getCommit();
        return commit.getMessage().startsWith(FIXUP_COMMIT_PREFIX);
    }

    public static void buildCheckRun(List<GHPullRequestCommitDetail> commitDetails, GHRepository repostitory,
            GHCommit headCommit, String checkRunName, String errorOutputTitle, String errorOutputSummary)
            throws IOException {

        if (commitDetails.isEmpty()) {
            repostitory.createCheckRun(checkRunName, headCommit.getSHA1())
                    .withStatus(Status.COMPLETED)
                    .withStartedAt(Date.from(Instant.now()))
                    .withConclusion(Conclusion.SUCCESS)
                    .create();
        } else {

            List<Annotation> annotations = new ArrayList<>();
            for (GHPullRequestCommitDetail commitDetail : commitDetails) {
                GHPullRequestCommitDetail.Commit commit = commitDetail.getCommit();

                String msg = String.format(ERROR_ANNOTATION_MSG, commitDetail.getSha(), commit.getMessage());
                Annotation annotation = new Annotation(".", 0, AnnotationLevel.FAILURE, msg)
                        .withTitle(ERROR_ANNOTATION_TITLE);
                annotations.add(annotation);
            }

            Output output = new Output(errorOutputTitle, errorOutputSummary);
            for (Annotation annotation : annotations) {
                output.add(annotation);
            }

            GHCheckRunBuilder check = repostitory
                    .createCheckRun(checkRunName, headCommit.getSHA1())
                    .withStatus(Status.COMPLETED)
                    .withStartedAt(Date.from(Instant.now()))
                    .withConclusion(Conclusion.FAILURE);
            check.add(output);
            check.create();
        }
    }

    private static String buildDryRunLogMessage(List<GHPullRequestCommitDetail> commitDetails, String checkRunName,
            String errorOutputSummary) {
        StringBuilder comment = new StringBuilder();
        comment.append(checkRunName);
        if (commitDetails.isEmpty()) {
            comment.append(">>> SUCCESS");
        } else {
            comment.append(">>> FAILURE");
            comment.append(errorOutputSummary);
            for (GHPullRequestCommitDetail commitDetail : commitDetails) {
                GHPullRequestCommitDetail.Commit commit = commitDetail.getCommit();
                comment.append(String.format(ERROR_ANNOTATION_MSG, commitDetail.getSha(), commit.getMessage()));
            }
        }
        return comment.toString();
    }
}
