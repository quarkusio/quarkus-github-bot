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
import org.apache.maven.reporting.MavenReportException;
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

    public Optional<String> getAnalysis(GHRepository workflowRepository,
            GHPullRequest pullRequest,
            List<GHArtifact> surefireReportsArtifacts)
            throws IOException, MavenReportException {
        Path allSurefireReportsDirectory = Files.createTempDirectory("surefire-reports-analyzer-");

        Report report = new Report(pullRequest.getHead().getRepository().getFullName(),
                pullRequest.getHead().getSha(),
                workflowRepository.getFullName().equals(pullRequest.getHead().getRepository().getFullName()));

        try {
            for (GHArtifact surefireReportsArtifact : surefireReportsArtifacts) {
                Job job = new Job(surefireReportsArtifact.getName()
                        .replace(AnalyzeWorkflowRunResults.SUREFIRE_REPORTS_ARTIFACT_PREFIX, ""));
                Path jobDirectory = allSurefireReportsDirectory.resolve(surefireReportsArtifact.getName());

                Set<Path> surefireReportsDirectories = surefireReportsArtifact
                        .download((is) -> unzip(is, jobDirectory));

                for (Path surefireReportsDirectory : surefireReportsDirectories) {
                    SurefireReportParser surefireReportsParser = new SurefireReportParser(
                            Collections.singletonList(surefireReportsDirectory.toFile()), Locale.ENGLISH,
                            new NullConsoleLogger());
                    List<ReportTestSuite> reportTestSuites = surefireReportsParser.parseXMLReportFiles();
                    Module module = new Module(
                            jobDirectory.relativize(surefireReportsDirectory).getParent().getParent().toString(),
                            reportTestSuites,
                            surefireReportsParser.getFailureDetails(reportTestSuites).stream().filter(rtc -> !rtc.hasSkipped())
                                    .collect(Collectors.toList()));
                    job.addModule(module);
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

    public static Set<Path> unzip(InputStream inputStream, Path destinationDirectory) throws IOException {
        Set<Path> surefireReportsDirectories = new TreeSet<>();

        try (final ZipInputStream zis = new ZipInputStream(inputStream)) {
            final byte[] buffer = new byte[1024];
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                final Path newPath = getZipEntryPath(destinationDirectory, zipEntry);
                final File newFile = newPath.toFile();
                if (newPath.endsWith("surefire-reports")) {
                    surefireReportsDirectories.add(newPath);
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

        return surefireReportsDirectories;
    }

    public static Path getZipEntryPath(Path destinationDirectory, ZipEntry zipEntry) throws IOException {
        Path destinationFile = destinationDirectory.resolve(zipEntry.getName());

        if (!destinationFile.toAbsolutePath().startsWith(destinationDirectory.toAbsolutePath())) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destinationFile;
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
        private final List<Module> modules = new ArrayList<>();

        private Job(String name) {
            this.name = name;
        }

        private void addModule(Module module) {
            this.modules.add(module);
        }

        public String getName() {
            return name;
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
