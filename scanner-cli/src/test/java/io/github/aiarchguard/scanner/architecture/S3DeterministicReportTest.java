package io.github.aiarchguard.scanner.architecture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aiarchguard.scanner.parser.java.JavaScanRequest;
import io.github.aiarchguard.scanner.parser.java.JavaScanResult;
import io.github.aiarchguard.scanner.parser.java.JavaSourceScanner;
import io.github.aiarchguard.scanner.report.DomainReport;
import io.github.aiarchguard.scanner.report.ReportJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class S3DeterministicReportTest {

    @Test
    void repeatedJavaExtractionProducesIdenticalContractBytes() throws IOException {
        Path fixture = reactorRoot().resolve("scanner-parser-java/src/test/resources/fixtures/multi-module");
        JavaScanRequest request = JavaScanRequest.forSchema010(fixture, "multi-module", "Multi Module");
        JavaSourceScanner scanner = new JavaSourceScanner();
        ReportJson reportJson = new ReportJson();

        JavaScanResult first = scanner.scan(request);
        JavaScanResult second = scanner.scan(request);
        byte[] firstBytes = reportJson.serialize(report(first));
        byte[] secondBytes = reportJson.serialize(report(second));

        assertTrue(first.diagnostics().isEmpty());
        assertTrue(second.diagnostics().isEmpty());
        assertArrayEquals(firstBytes, secondBytes);
    }

    private static DomainReport report(JavaScanResult result) {
        return new DomainReport(
                result.project(),
                result.artifacts(),
                result.components(),
                result.dependencies(),
                result.metrics(),
                List.of(),
                result.evidences());
    }

    private static Path reactorRoot() throws IOException {
        String configuredRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (configuredRoot != null) {
            Path candidate = Path.of(configuredRoot).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate.resolve("scanner-domain/pom.xml"))) {
                return candidate;
            }
        }
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("scanner-domain/pom.xml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IOException("Cannot locate the ArchGuard Scanner reactor root");
    }
}
