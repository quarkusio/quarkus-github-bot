package io.quarkus.bot.workflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowJob.Step;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkus.bot.workflow.SurefireReportsUnarchiver.TestResultsPath;
import io.quarkus.bot.workflow.report.WorkflowReport;
import io.quarkus.bot.workflow.report.WorkflowReportJob;
import io.quarkus.bot.workflow.report.WorkflowReportModule;
import io.quarkus.bot.workflow.report.WorkflowReportTestCase;
import io.quarkus.bot.workflow.urlshortener.UrlShortener;

@ApplicationScoped
public class WorkflowRunAnalyzer {

    private static final Logger LOG = Logger.getLogger(WorkflowRunAnalyzer.class);

    @Inject
    SurefireReportsUnarchiver surefireReportsUnarchiver;

    @Inject
    UrlShortener urlShortener;

    public Optional<WorkflowReport> getReport(GHWorkflowRun workflowRun,
            GHPullRequest pullRequest,
            List<GHWorkflowJob> jobs,
            List<GHArtifact> surefireReportsArtifacts) throws IOException {
        if (jobs.isEmpty()) {
            return Optional.empty();
        }

        GHRepository workflowRunRepository = workflowRun.getRepository();
        String pullRequestRepositoryName = pullRequest.getHead().getRepository().getFullName();
        String sha = workflowRun.getHeadSha();
        Map<String, String> testFailuresAnchors = getTestFailuresAnchors(jobs, surefireReportsArtifacts);
        Path allSurefireReportsDirectory = Files.createTempDirectory("surefire-reports-analyzer-");

        try {
            List<WorkflowReportJob> workflowReportJobs = new ArrayList<>();

            for (GHWorkflowJob job : jobs) {
                if (job.getConclusion() != Conclusion.FAILURE && job.getConclusion() != Conclusion.CANCELLED) {
                    continue;
                }

                Optional<GHArtifact> surefireReportsArtifactOptional = surefireReportsArtifacts.stream()
                        .filter(a -> a.getName().replace(WorkflowConstants.SUREFIRE_REPORTS_ARTIFACT_PREFIX, "")
                                .equals(job.getName()))
                        .findFirst();

                List<WorkflowReportModule> modules = Collections.emptyList();
                boolean errorDownloadingSurefireReports = false;
                if (surefireReportsArtifactOptional.isPresent()) {
                    GHArtifact surefireReportsArtifact = surefireReportsArtifactOptional.get();
                    Path jobDirectory = allSurefireReportsDirectory.resolve(surefireReportsArtifact.getName());
                    Set<TestResultsPath> testResultsPaths = Collections.emptySet();
                    try {
                        testResultsPaths = surefireReportsUnarchiver.getTestResults(jobDirectory,
                                surefireReportsArtifact);
                        modules = surefireReportsArtifactOptional.isPresent()
                                ? getModules(pullRequest, jobDirectory, testResultsPaths, pullRequestRepositoryName, sha)
                                : Collections.emptyList();
                    } catch (Exception e) {
                        errorDownloadingSurefireReports = true;
                        LOG.error("Pull request #" + pullRequest.getNumber() + " - Unable to download artifact "
                                + surefireReportsArtifact.getName());
                    }
                }

                workflowReportJobs.add(new WorkflowReportJob(job.getName(), testFailuresAnchors.get(job.getName()),
                        job.getConclusion(), getFailingStep(job.getSteps()),
                        getJobUrl(job),
                        getRawLogsUrl(job, workflowRun.getHeadSha()),
                        modules,
                        errorDownloadingSurefireReports));
            }

            if (workflowReportJobs.isEmpty()) {
                return Optional.empty();
            }

            WorkflowReport report = new WorkflowReport(sha, workflowReportJobs,
                    workflowRunRepository.getFullName().equals(pullRequestRepositoryName));

            return Optional.of(report);
        } finally {
            try {
                Files.walk(allSurefireReportsDirectory)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                LOG.error("Pull request #" + pullRequest.getNumber() + " - Unable to delete temp directory "
                        + allSurefireReportsDirectory);
            }
        }
    }

    public List<WorkflowReportModule> getModules(GHPullRequest pullRequest, Path jobDirectory,
            Set<TestResultsPath> testResultsPaths,
            String pullRequestRepository, String sha) {
        List<WorkflowReportModule> modules = new ArrayList<>();

        for (TestResultsPath testResultPath : testResultsPaths) {
            try {
                SurefireReportParser surefireReportsParser = new SurefireReportParser(
                        Collections.singletonList(testResultPath.getPath().toFile()), Locale.ENGLISH,
                        new NullConsoleLogger());
                List<ReportTestSuite> reportTestSuites = surefireReportsParser.parseXMLReportFiles();
                String moduleName = testResultPath.getModuleName(jobDirectory);
                WorkflowReportModule module = new WorkflowReportModule(
                        moduleName,
                        reportTestSuites,
                        surefireReportsParser.getFailureDetails(reportTestSuites).stream()
                                .filter(rtc -> !rtc.hasSkipped())
                                .sorted((rtc1, rtc2) -> rtc1.getFullClassName().compareTo(rtc2.getFullClassName()))
                                .map(rtc -> new WorkflowReportTestCase(
                                        WorkflowUtils.getFilePath(moduleName, rtc.getFullClassName()),
                                        rtc,
                                        getFailureUrl(pullRequestRepository, sha, moduleName, rtc),
                                        urlShortener.shorten(getFailureUrl(pullRequestRepository, sha, moduleName, rtc))))
                                .collect(Collectors.toList()));
                if (module.hasTestFailures()) {
                    modules.add(module);
                }
            } catch (Exception e) {
                LOG.error("Pull request #" + pullRequest.getNumber() + " - Unable to parse test results for file "
                        + testResultPath.getPath(), e);
            }
        }

        return modules;
    }

    private static Map<String, String> getTestFailuresAnchors(List<GHWorkflowJob> jobs,
            List<GHArtifact> surefireReportsArtifacts) {
        Set<String> surefireReportsArtifactNames = surefireReportsArtifacts.stream()
                .map(a -> a.getName().replace(WorkflowConstants.SUREFIRE_REPORTS_ARTIFACT_PREFIX, ""))
                .collect(Collectors.toSet());

        Map<String, String> testFailuresAnchors = new HashMap<>();
        for (GHWorkflowJob job : jobs) {
            if (!surefireReportsArtifactNames.contains(job.getName())) {
                continue;
            }
            testFailuresAnchors.put(job.getName(), "test-failures-job-" + job.getId());
        }
        return testFailuresAnchors;
    }

    private static String getFailingStep(List<Step> steps) {
        for (Step step : steps) {
            if (step.getConclusion() != Conclusion.SUCCESS) {
                return step.getName();
            }
        }
        return null;
    }

    private String getJobUrl(GHWorkflowJob job) {
        return urlShortener.shorten(job.getHtmlUrl() + "?check_suite_focus=true");
    }

    private String getRawLogsUrl(GHWorkflowJob job, String sha) {
        return urlShortener.shorten(job.getHtmlUrl().toString().replace("/runs/", "/commit/" + sha + "/checks/")
                + "/logs");
    }

    private static String getFailureUrl(String repository, String sha, String moduleName, ReportTestCase reportTestCase) {
        String classPath = reportTestCase.getFullClassName().replace(".", "/");
        int dollarIndex = reportTestCase.getFullClassName().indexOf('$');
        if (dollarIndex > 0) {
            classPath = classPath.substring(0, dollarIndex);
        }
        classPath = "src/test/java/" + classPath + ".java";

        StringBuilder sb = new StringBuilder();
        sb.append("https://github.com/").append(repository).append("/blob/").append(sha).append("/")
                .append(WorkflowUtils.getFilePath(moduleName, reportTestCase.getFullClassName()));
        if (reportTestCase.getFailureErrorLine() != null) {
            sb.append("#L").append(reportTestCase.getFailureErrorLine());
        }
        return sb.toString();
    }
}
