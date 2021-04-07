package io.quarkus.bot.workflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;

import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowJob.Step;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@ApplicationScoped
public class JobReportsAnalyzer {

    public Optional<String> getAnalysis(GHWorkflowRun workflowRun, List<GHWorkflowJob> jobs,
            Map<String, String> testFailuresAnchors)
            throws IOException {
        if (jobs.isEmpty()) {
            return Optional.empty();
        }

        Report report = new Report();

        for (GHWorkflowJob job : jobs) {
            if (job.getConclusion() != Conclusion.FAILURE && job.getConclusion() != Conclusion.CANCELLED) {
                continue;
            }

            report.addJob(new Job(job.getName(), testFailuresAnchors.get(job.getName()),
                    job.getConclusion(), getFailingStep(job.getSteps()),
                    job.getHtmlUrl() + "?check_suite_focus=true",
                    job.getHtmlUrl().toString().replace("/runs/", "/commit/" + workflowRun.getHeadSha() + "/checks/")
                            + "/logs"));
        }

        if (report.getJobs().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Templates.report(report).render());
    }

    private static String getFailingStep(List<Step> steps) {
        for (Step step : steps) {
            if (step.getConclusion() != Conclusion.SUCCESS) {
                return step.getName();
            }
        }
        return null;
    }

    public static class Report {

        private List<Job> jobs = new ArrayList<>();

        private void addJob(Job job) {
            this.jobs.add(job);
        }

        public List<Job> getJobs() {
            return jobs;
        }
    }

    public static class Job {

        private final String name;
        private final String testFailuresAnchor;
        private final Conclusion conclusion;
        private final String failingStep;
        private final String url;
        private final String rawLogsUrl;

        public Job(String name, String testFailuresAnchor, Conclusion conclusion, String failingStep, String url,
                String rawLogsUrl) {
            this.name = name;
            this.testFailuresAnchor = testFailuresAnchor;
            this.conclusion = conclusion;
            this.failingStep = failingStep;
            this.url = url;
            this.rawLogsUrl = rawLogsUrl;
        }

        public String getName() {
            return name;
        }

        public String getTestFailuresAnchor() {
            return testFailuresAnchor;
        }

        public String getConclusionEmoji() {
            switch (conclusion) {
                case FAILURE:
                    return ":x:";
                case CANCELLED:
                    return ":hourglass:";
                default:
                    return ":question:";
            }
        }

        public String getFailingStep() {
            return failingStep;
        }

        public String getUrl() {
            return url;
        }

        public String getRawLogsUrl() {
            return rawLogsUrl;
        }
    }

    @CheckedTemplate
    private static class Templates {

        public static native TemplateInstance report(Report report);
    }
}
