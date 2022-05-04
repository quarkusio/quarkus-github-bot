package io.quarkus.bot.workflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowJob.Step;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Conclusion;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.bot.build.reporting.model.BuildReport;
import io.quarkus.bot.build.reporting.model.ProjectReport;
import io.quarkus.bot.workflow.BuildReportsUnarchiver.BuildReports;
import io.quarkus.bot.workflow.BuildReportsUnarchiver.TestResultsPath;
import io.quarkus.bot.workflow.report.WorkflowReport;
import io.quarkus.bot.workflow.report.WorkflowReportJob;
import io.quarkus.bot.workflow.report.WorkflowReportModule;
import io.quarkus.bot.workflow.report.WorkflowReportTestCase;
import io.quarkus.bot.workflow.urlshortener.UrlShortener;
import io.quarkus.runtime.annotations.RegisterForReflection;

@ApplicationScoped
@RegisterForReflection(targets = { BuildReport.class, ProjectReport.class })
public class WorkflowRunAnalyzer {

    private static final Logger LOG = Logger.getLogger(WorkflowRunAnalyzer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BuildReport EMPTY_BUILD_REPORT = new BuildReport();

    @Inject
    BuildReportsUnarchiver buildReportsUnarchiver;

    @Inject
    UrlShortener urlShortener;

    public Optional<WorkflowReport> getReport(GHWorkflowRun workflowRun,
            WorkflowContext workflowContext,
            List<GHWorkflowJob> jobs,
            List<GHArtifact> buildReportsArtifacts) throws IOException {
        if (jobs.isEmpty()) {
            LOG.error(workflowContext.getLogContext() + " - No jobs found");
            return Optional.empty();
        }

        GHRepository workflowRunRepository = workflowRun.getRepository();
        String sha = workflowRun.getHeadSha();
        Path allBuildReportsDirectory = Files.createTempDirectory("build-reports-analyzer-");

        try {
            List<WorkflowReportJob> workflowReportJobs = new ArrayList<>();

            for (GHWorkflowJob job : jobs) {
                if (job.getConclusion() != Conclusion.FAILURE && job.getConclusion() != Conclusion.CANCELLED) {
                    workflowReportJobs.add(new WorkflowReportJob(job.getName(), null, job.getConclusion(), null, null, null,
                            EMPTY_BUILD_REPORT, Collections.emptyList(), false));
                    continue;
                }

                Optional<GHArtifact> buildReportsArtifactOptional = buildReportsArtifacts.stream()
                        .filter(a -> a.getName().replace(WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX, "")
                                .equals(job.getName()))
                        .findFirst();

                BuildReport buildReport = EMPTY_BUILD_REPORT;
                List<WorkflowReportModule> modules = Collections.emptyList();
                boolean errorDownloadingBuildReports = false;
                if (buildReportsArtifactOptional.isPresent()) {
                    GHArtifact buildReportsArtifact = buildReportsArtifactOptional.get();
                    Path jobDirectory = allBuildReportsDirectory.resolve(buildReportsArtifact.getName());

                    Optional<BuildReports> buildReportsOptional = buildReportsUnarchiver.getBuildReports(workflowContext,
                            buildReportsArtifact, jobDirectory);

                    if (buildReportsOptional.isPresent()) {
                        BuildReports buildReports = buildReportsOptional.get();
                        if (buildReports.getBuildReportPath() != null) {
                            buildReport = getBuildReport(workflowContext, buildReports.getBuildReportPath());
                        }

                        modules = buildReportsArtifactOptional.isPresent()
                                ? getModules(workflowContext, buildReport, jobDirectory, buildReports.getTestResultsPaths(),
                                        sha)
                                : Collections.emptyList();
                    } else {
                        errorDownloadingBuildReports = true;
                        LOG.error(workflowContext.getLogContext() + " - Unable to analyze build report for artifact "
                                + buildReportsArtifact.getName() + " - see exceptions above");
                    }
                }

                workflowReportJobs.add(new WorkflowReportJob(job.getName(),
                        getFailuresAnchor(job.getId()),
                        job.getConclusion(),
                        getFailingStep(job.getSteps()),
                        getJobUrl(job),
                        getRawLogsUrl(job, workflowRun.getHeadSha()),
                        buildReport,
                        modules,
                        errorDownloadingBuildReports));
            }

            if (workflowReportJobs.isEmpty()) {
                LOG.warn(workflowContext.getLogContext() + " - Report jobs empty");
                return Optional.empty();
            }

            WorkflowReport report = new WorkflowReport(sha, workflowReportJobs,
                    workflowRunRepository.getFullName().equals(workflowContext.getRepository()),
                    workflowRun.getConclusion(), workflowRun.getHtmlUrl().toString());

            return Optional.of(report);
        } finally {
            try {
                Files.walk(allBuildReportsDirectory)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                LOG.error(workflowContext.getLogContext() + " - Unable to delete temp directory "
                        + allBuildReportsDirectory);
            }
        }
    }

    private static BuildReport getBuildReport(WorkflowContext workflowContext, Path buildReportPath) {
        if (buildReportPath == null) {
            return new BuildReport();
        }

        try {
            return OBJECT_MAPPER.readValue(buildReportPath.toFile(), BuildReport.class);
        } catch (Exception e) {
            LOG.error(workflowContext.getLogContext() + " - Unable to deserialize "
                    + WorkflowConstants.BUILD_REPORT_PATH, e);
            return new BuildReport();
        }
    }

    private List<WorkflowReportModule> getModules(
            WorkflowContext workflowContext,
            BuildReport buildReport,
            Path jobDirectory,
            Set<TestResultsPath> testResultsPaths,
            String sha) {
        List<WorkflowReportModule> modules = new ArrayList<>();

        Map<String, ModuleReports> moduleReportsMap = mapModuleReports(buildReport, testResultsPaths, jobDirectory);

        for (Entry<String, ModuleReports> moduleReportsEntry : moduleReportsMap.entrySet()) {
            String moduleName = moduleReportsEntry.getKey();
            ModuleReports moduleReports = moduleReportsEntry.getValue();

            List<ReportTestSuite> reportTestSuites = new ArrayList<>();
            List<WorkflowReportTestCase> workflowReportTestCases = new ArrayList<>();
            for (TestResultsPath testResultPath : moduleReports.getTestResultsPaths()) {
                try {
                    SurefireReportParser surefireReportsParser = new SurefireReportParser(
                            Collections.singletonList(testResultPath.getPath().toFile()), Locale.ENGLISH,
                            new NullConsoleLogger());
                    reportTestSuites.addAll(surefireReportsParser.parseXMLReportFiles());
                    workflowReportTestCases.addAll(surefireReportsParser.getFailureDetails(reportTestSuites).stream()
                            .filter(rtc -> !rtc.hasSkipped())
                            .map(rtc -> new WorkflowReportTestCase(
                                    WorkflowUtils.getFilePath(moduleName, rtc.getFullClassName()),
                                    rtc,
                                    StackTraceUtils.firstLines(StackTraceUtils.abbreviate(rtc.getFailureDetail(), 1000), 3),
                                    getFailureUrl(workflowContext.getRepository(), sha, moduleName, rtc),
                                    urlShortener.shorten(getFailureUrl(workflowContext.getRepository(), sha, moduleName, rtc))))
                            .collect(Collectors.toList()));
                } catch (Exception e) {
                    LOG.error(workflowContext.getLogContext() + " - Unable to parse test results for file "
                            + testResultPath.getPath(), e);
                }
            }

            WorkflowReportModule module = new WorkflowReportModule(
                    moduleName,
                    moduleReports.getProjectReport(),
                    reportTestSuites,
                    workflowReportTestCases);

            if (module.hasReportedFailures()) {
                modules.add(module);
            }
        }

        return modules;
    }

    private static Map<String, ModuleReports> mapModuleReports(BuildReport buildReport, Set<TestResultsPath> testResultsPaths,
            Path jobDirectory) {
        Set<String> modules = new TreeSet<>();
        modules.addAll(buildReport.getProjectReports().stream().map(pr -> normalizeModuleName(pr.getBasedir()))
                .collect(Collectors.toList()));
        modules.addAll(testResultsPaths.stream().map(trp -> normalizeModuleName(trp.getModuleName(jobDirectory)))
                .collect(Collectors.toList()));

        Map<String, ModuleReports> moduleReports = new TreeMap<>();
        for (String module : modules) {
            moduleReports.put(module, new ModuleReports(
                    buildReport.getProjectReports().stream().filter(pr -> normalizeModuleName(pr.getBasedir()).equals(module))
                            .findFirst().orElse(null),
                    testResultsPaths.stream().filter(trp -> normalizeModuleName(trp.getModuleName(jobDirectory)).equals(module))
                            .collect(Collectors.toList())));
        }

        return moduleReports;
    }

    private static String normalizeModuleName(String moduleName) {
        return moduleName.replace('\\', '/');
    }

    private static String getFailuresAnchor(Long jobId) {
        return "test-failures-job-" + jobId;
    }

    private static String getFailingStep(List<Step> steps) {
        for (Step step : steps) {
            if (step.getConclusion() != Conclusion.SUCCESS && step.getConclusion() != Conclusion.SKIPPED
                    && step.getConclusion() != Conclusion.NEUTRAL) {
                return step.getName();
            }
        }
        return null;
    }

    private String getJobUrl(GHWorkflowJob job) {
        return urlShortener.shorten(job.getHtmlUrl().toString());
    }

    private String getRawLogsUrl(GHWorkflowJob job, String sha) {
        return urlShortener.shorten(job.getRepository().getHtmlUrl().toString() +
                "/commit/" + sha + "/checks/" + job.getId() + "/logs");
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
        if (StringUtils.isNotBlank(reportTestCase.getFailureErrorLine())) {
            sb.append("#L").append(reportTestCase.getFailureErrorLine());
        }
        return sb.toString();
    }

    private static class ModuleReports {

        private final ProjectReport projectReport;
        private final List<TestResultsPath> testResultsPaths;

        private ModuleReports(ProjectReport projectReport, List<TestResultsPath> testResultsPaths) {
            this.projectReport = projectReport;
            this.testResultsPaths = testResultsPaths;
        }

        public ProjectReport getProjectReport() {
            return projectReport;
        }

        public List<TestResultsPath> getTestResultsPaths() {
            return testResultsPaths;
        }
    }
}
