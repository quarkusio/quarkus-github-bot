package io.quarkus.bot.workflow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.enterprise.context.ApplicationScoped;

import org.kohsuke.github.GHArtifact;

@ApplicationScoped
class SurefireReportsUnarchiver {

    private static final Path MAVEN_SUREFIRE_REPORTS_PATH = Path.of("target", "surefire-reports");
    private static final Path MAVEN_FAILSAFE_REPORTS_PATH = Path.of("target", "failsafe-reports");
    private static final Path GRADLE_REPORTS_PATH = Path.of("build", "test-results", "test");

    public Set<TestResultsPath> getTestResults(Path jobDirectory, GHArtifact surefireReportsArtifact) throws IOException {
        return surefireReportsArtifact
                .download((is) -> unzip(is, jobDirectory));
    }

    public Set<TestResultsPath> unzip(InputStream inputStream, Path destinationDirectory) throws IOException {
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

    static Path getZipEntryPath(Path destinationDirectory, ZipEntry zipEntry) throws IOException {
        Path destinationFile = destinationDirectory.resolve(zipEntry.getName());

        if (!destinationFile.toAbsolutePath().startsWith(destinationDirectory.toAbsolutePath())) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destinationFile;
    }

    interface TestResultsPath extends Comparable<TestResultsPath> {

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

    static class FailsafeTestResultsPath implements TestResultsPath {

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

    static class GradleTestResultsPath implements TestResultsPath {

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
}
