package io.quarkus.bot.workflow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.enterprise.context.ApplicationScoped;

import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import io.quarkus.bot.AnalyzeWorkflowRunResults;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.annotations.RegisterForReflection;

@ApplicationScoped
public class SurefireReportsAnalyzer {

    private static final Logger LOG = Logger.getLogger(SurefireReportsAnalyzer.class);

    private static final Path MAVEN_SUREFIRE_REPORTS_PATH = Path.of("target", "surefire-reports");
    private static final Path MAVEN_FAILSAFE_REPORTS_PATH = Path.of("target", "failsafe-reports");
    private static final Path GRADLE_REPORTS_PATH = Path.of("build", "test-results", "test");

    public Optional<String> getAnalysis(GHRepository workflowRepository,
            GHPullRequest pullRequest,
            List<GHArtifact> surefireReportsArtifacts,
            Map<String, String> testFailuresAnchors)
            throws IOException {
        if (surefireReportsArtifacts.isEmpty()) {
            return Optional.empty();
        }

        Path allSurefireReportsDirectory = Files.createTempDirectory("surefire-reports-analyzer-");

        Report report = new Report(pullRequest.getHead().getRepository().getFullName(),
                pullRequest.getHead().getSha(),
                workflowRepository.getFullName().equals(pullRequest.getHead().getRepository().getFullName()));

        try {
            for (GHArtifact surefireReportsArtifact : surefireReportsArtifacts) {
                String jobName = surefireReportsArtifact.getName()
                        .replace(AnalyzeWorkflowRunResults.SUREFIRE_REPORTS_ARTIFACT_PREFIX, "");
                Job job = new Job(jobName, testFailuresAnchors.get(jobName));
                Path jobDirectory = allSurefireReportsDirectory.resolve(surefireReportsArtifact.getName());

                Set<TestResultsPath> testResultsPath;
                try {
                    testResultsPath = surefireReportsArtifact
                            .download((is) -> unzip(is, jobDirectory));
                } catch (Exception e) {
                    LOG.error("Unable to download artifact " + surefireReportsArtifact.getName());
                    continue;
                }

                for (TestResultsPath testResultPath : testResultsPath) {
                    try {
                        SurefireReportParser surefireReportsParser = new SurefireReportParser(
                                Collections.singletonList(testResultPath.getPath().toFile()), Locale.ENGLISH,
                                new NullConsoleLogger());
                        List<ReportTestSuite> reportTestSuites = surefireReportsParser.parseXMLReportFiles();
                        Module module = new Module(
                                testResultPath.getModuleName(jobDirectory),
                                reportTestSuites,
                                surefireReportsParser.getFailureDetails(reportTestSuites).stream()
                                        .filter(rtc -> !rtc.hasSkipped())
                                        .sorted((rtc1, rtc2) -> rtc1.getFullClassName().compareTo(rtc2.getFullClassName()))
                                        .collect(Collectors.toList()));
                        job.addModule(module);
                    } catch (Exception e) {
                        LOG.error("Unable to parse test results for file " + testResultPath.getPath(), e);
                    }
                }

                report.addJob(job);
            }

            if (!report.hasErrors()) {
                return Optional.empty();
            }

            return Optional.of(Templates.report(report).render());
        } finally {
            Files.walk(allSurefireReportsDirectory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    public static Set<TestResultsPath> unzip(InputStream inputStream, Path destinationDirectory) throws IOException {
        Set<TestResultsPath> testResultsPaths = new TreeSet<>();

        try (final ZipInputStream zis = new ZipInputStream(inputStream)) {
            final byte[] buffer = new byte[1024];
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                final Path newPath = getZipEntryPath(destinationDirectory, zipEntry);
                final File newFile = newPath.toFile();
                if (newPath.endsWith(MAVEN_SUREFIRE_REPORTS_PATH)) {
                    testResultsPaths.add(new SurefireTestResultsPath(newPath));
                } else if (newPath.endsWith(MAVEN_FAILSAFE_REPORTS_PATH)) {
                    testResultsPaths.add(new FailsafeTestResultsPath(newPath));
                } else if (newPath.endsWith(GRADLE_REPORTS_PATH)) {
                    testResultsPaths.add(new GradleTestResultsPath(newPath));
                }
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    final FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }

        return testResultsPaths;
    }

    public static Path getZipEntryPath(Path destinationDirectory, ZipEntry zipEntry) throws IOException {
        Path destinationFile = destinationDirectory.resolve(zipEntry.getName());

        if (!destinationFile.toAbsolutePath().startsWith(destinationDirectory.toAbsolutePath())) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destinationFile;
    }

    private interface TestResultsPath extends Comparable<TestResultsPath> {

        Path getPath();

        String getModuleName(Path jobDirectory);
    }

    private static class SurefireTestResultsPath implements TestResultsPath {

        private final Path path;

        private SurefireTestResultsPath(Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public String getModuleName(Path jobDirectory) {
            return jobDirectory.relativize(path).getParent().getParent().toString();
        }

        @Override
        public int compareTo(TestResultsPath o) {
            return path.compareTo(o.getPath());
        }
    }

    private static class FailsafeTestResultsPath implements TestResultsPath {

        private final Path path;

        private FailsafeTestResultsPath(Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public String getModuleName(Path jobDirectory) {
            return jobDirectory.relativize(path).getParent().getParent().toString();
        }

        @Override
        public int compareTo(TestResultsPath o) {
            return path.compareTo(o.getPath());
        }
    }

    private static class GradleTestResultsPath implements TestResultsPath {

        private final Path path;

        private GradleTestResultsPath(Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public String getModuleName(Path jobDirectory) {
            return jobDirectory.relativize(path).getParent().getParent().getParent().toString();
        }

        @Override
        public int compareTo(TestResultsPath o) {
            return path.compareTo(o.getPath());
        }
    }

    @RegisterForReflection
    @SuppressWarnings("unused")
    private static class Report {

        private final List<Job> jobs = new ArrayList<>();
        private final String repository;
        private final String sha;
        private final boolean sameRepository;

        private Report(String repository, String sha, boolean sameRepository) {
            this.repository = repository;
            this.sha = sha;
            this.sameRepository = sameRepository;
        }

        public String getRepository() {
            return repository;
        }

        public String getSha() {
            return sha;
        }

        private void addJob(Job job) {
            this.jobs.add(job);
        }

        public List<Job> getJobs() {
            return jobs;
        }

        public boolean hasErrors() {
            for (Job job : jobs) {
                if (job.hasErrors()) {
                    return true;
                }
            }
            return false;
        }

        public boolean isSameRepository() {
            return sameRepository;
        }
    }

    @RegisterForReflection
    @SuppressWarnings("unused")
    private static class Job {

        private final String name;
        private final String testFailuresAnchor;
        private final List<Module> modules = new ArrayList<>();

        private Job(String name, String testFailuresAnchor) {
            this.name = name;
            this.testFailuresAnchor = testFailuresAnchor;
        }

        private void addModule(Module module) {
            this.modules.add(module);
        }

        public String getName() {
            return name;
        }

        public String getTestFailuresAnchor() {
            return testFailuresAnchor;
        }

        public List<Module> getModules() {
            return modules;
        }

        public boolean hasErrors() {
            for (Module module : modules) {
                if (module.hasErrors()) {
                    return true;
                }
            }
            return false;
        }
    }

    @RegisterForReflection
    @SuppressWarnings("unused")
    private static class Module {

        private final String name;
        private final List<ReportTestSuite> reportTestSuites;
        private final List<ReportTestCase> failures;

        private Module(String name, List<ReportTestSuite> reportTestSuites, List<ReportTestCase> failures) {
            this.name = name;
            this.reportTestSuites = reportTestSuites;
            this.failures = failures;
        }

        public String getName() {
            return name;
        }

        public boolean hasErrors() {
            for (ReportTestSuite reportTestSuite : reportTestSuites) {
                if (reportTestSuite.getNumberOfErrors() > 0 || reportTestSuite.getNumberOfFailures() > 0) {
                    return true;
                }
            }
            return false;
        }

        public List<ReportTestCase> getFailures() {
            return failures;
        }

        public int getTestCount() {
            int testCount = 0;
            for (ReportTestSuite reportTestSuite : reportTestSuites) {
                testCount += reportTestSuite.getNumberOfTests();
            }
            return testCount;
        }

        public int getSuccessCount() {
            int successCount = 0;
            for (ReportTestSuite reportTestSuite : reportTestSuites) {
                for (ReportTestCase reportTestCase : reportTestSuite.getTestCases()) {
                    if (reportTestCase.isSuccessful()) {
                        successCount++;
                    }
                }
            }
            return successCount;
        }

        public int getErrorCount() {
            int testCount = 0;
            for (ReportTestSuite reportTestSuite : reportTestSuites) {
                testCount += reportTestSuite.getNumberOfErrors();
            }
            return testCount;
        }

        public int getFailureCount() {
            int testCount = 0;
            for (ReportTestSuite reportTestSuite : reportTestSuites) {
                testCount += reportTestSuite.getNumberOfFailures();
            }
            return testCount;
        }

        public int getSkippedCount() {
            int testCount = 0;
            for (ReportTestSuite reportTestSuite : reportTestSuites) {
                testCount += reportTestSuite.getNumberOfSkipped();
            }
            return testCount;
        }
    }

    @CheckedTemplate
    private static class Templates {

        public static native TemplateInstance report(Report report);
    }

    @TemplateExtension
    public static class TemplateExtensions {

        public static String classPath(ReportTestCase reportTestCase) {
            String classPath = reportTestCase.getFullClassName().replace(".", "/");
            int dollarIndex = reportTestCase.getFullClassName().indexOf('$');
            if (dollarIndex > 0) {
                classPath = classPath.substring(0, dollarIndex);
            }
            return "src/test/java/" + classPath + ".java";
        }
    }
}
