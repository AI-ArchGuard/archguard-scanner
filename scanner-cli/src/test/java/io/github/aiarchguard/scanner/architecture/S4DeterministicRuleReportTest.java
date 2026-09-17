package io.github.aiarchguard.scanner.architecture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aiarchguard.scanner.domain.model.Finding;
import io.github.aiarchguard.scanner.domain.model.Severity;
import io.github.aiarchguard.scanner.parser.java.JavaScanRequest;
import io.github.aiarchguard.scanner.parser.java.JavaScanResult;
import io.github.aiarchguard.scanner.parser.java.JavaSourceScanner;
import io.github.aiarchguard.scanner.report.DomainReport;
import io.github.aiarchguard.scanner.report.ReportJson;
import io.github.aiarchguard.scanner.rule.ArchitectureLayer;
import io.github.aiarchguard.scanner.rule.CycleScope;
import io.github.aiarchguard.scanner.rule.DependencyCycleConfig;
import io.github.aiarchguard.scanner.rule.IllegalPackageDependencyConfig;
import io.github.aiarchguard.scanner.rule.LayeredArchitectureConfig;
import io.github.aiarchguard.scanner.rule.PackagePolicy;
import io.github.aiarchguard.scanner.rule.PackageSelector;
import io.github.aiarchguard.scanner.rule.RuleExecutionLimits;
import io.github.aiarchguard.scanner.rule.RuleInput;
import io.github.aiarchguard.scanner.rule.StructureRuleConfiguration;
import io.github.aiarchguard.scanner.rule.StructureRuleEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class S4DeterministicRuleReportTest {

    @Test
    void parserRulesAndReportProduceIdenticalValidatedContractBytes() throws IOException {
        Path fixture = reactorRoot().resolve("scanner-parser-java/src/test/resources/fixtures/multi-module");
        JavaScanRequest request = JavaScanRequest.forSchema010(fixture, "multi-module", "Multi Module");
        JavaSourceScanner scanner = new JavaSourceScanner();
        StructureRuleEngine engine = new StructureRuleEngine();
        ReportJson reportJson = new ReportJson();

        JavaScanResult firstScan = scanner.scan(request);
        JavaScanResult secondScan = scanner.scan(request);
        List<StructureRuleConfiguration> configurations = configurations();
        List<Finding> firstFindings = engine.execute(
                        input(firstScan), configurations, RuleExecutionLimits.defaults())
                .findings();
        List<Finding> secondFindings = engine.execute(
                        input(secondScan), configurations.reversed(), RuleExecutionLimits.defaults())
                .findings();
        byte[] firstBytes = reportJson.serialize(report(firstScan, firstFindings));
        byte[] secondBytes = reportJson.serialize(report(secondScan, secondFindings));

        assertTrue(firstScan.diagnostics().isEmpty());
        assertEquals(2, firstFindings.size());
        assertEquals(
                Set.of("archguard.illegal-package-dependency", "archguard.layered-architecture"),
                firstFindings.stream().map(finding -> finding.rule().id()).collect(Collectors.toSet()));
        assertArrayEquals(firstBytes, secondBytes);
        reportJson.validate(firstBytes);
    }

    private static List<StructureRuleConfiguration> configurations() {
        return List.of(
                new IllegalPackageDependencyConfig(
                        List.of(new PackagePolicy(
                                new PackageSelector("com.example.app"),
                                List.of(new PackageSelector("com.example.api")),
                                List.of(new PackageSelector("com.example.infra")))),
                        Severity.HIGH),
                new LayeredArchitectureConfig(
                        List.of(
                                layer("application", "com.example.app"),
                                layer("api", "com.example.api"),
                                layer("infrastructure", "com.example.infra")),
                        false,
                        Severity.MEDIUM),
                new DependencyCycleConfig(CycleScope.COMPONENT, Severity.HIGH));
    }

    private static ArchitectureLayer layer(String name, String packageName) {
        return new ArchitectureLayer(name, List.of(new PackageSelector(packageName)));
    }

    private static RuleInput input(JavaScanResult result) {
        return new RuleInput(
                "0.1.0",
                result.project(),
                result.artifacts(),
                result.components(),
                result.dependencies(),
                result.evidences());
    }

    private static DomainReport report(JavaScanResult result, List<Finding> findings) {
        return new DomainReport(
                result.project(),
                result.artifacts(),
                result.components(),
                result.dependencies(),
                List.of(),
                findings,
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
