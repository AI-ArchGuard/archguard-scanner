package io.github.aiarchguard.scanner.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aiarchguard.scanner.report.ReportJson;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerCliTest {

    @TempDir
    Path temporaryDirectory;

    private Path repository;

    @BeforeEach
    void createRepository() throws Exception {
        repository = temporaryDirectory.resolve("repository");
        Files.createDirectories(repository.resolve("src/main/java/example/web"));
        Files.createDirectories(repository.resolve("src/main/java/example/data"));
        Files.writeString(
                repository.resolve("pom.xml"),
                "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>"
                        + "<artifactId>application</artifactId><version>1</version></project>");
        Files.writeString(
                repository.resolve("src/main/java/example/data/Repository.java"),
                "package example.data; public class Repository {}\n");
        Files.writeString(
                repository.resolve("src/main/java/example/web/Controller.java"),
                "package example.web; import example.data.Repository; "
                        + "public class Controller { private Repository repository; }\n");
    }

    @Test
    void producesSchemaValidByteStableReports() throws Exception {
        Path rules = writeRules("archguard.dependency-cycle", "scope: component", "high");
        Path first = temporaryDirectory.resolve("first.json");
        Path second = temporaryDirectory.resolve("second.json");

        Invocation firstRun = invoke(repository, rules, first);
        Invocation secondRun = invoke(repository, rules, second);

        assertEquals(CliExitCode.SUCCESS.value(), firstRun.exitCode());
        assertEquals(CliExitCode.SUCCESS.value(), secondRun.exitCode());
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        new ReportJson().validate(Files.readAllBytes(first));
        assertTrue(firstRun.stdout().contains("findings=0 diagnostics=0"));
        assertEquals("", firstRun.stderr());
    }

    @Test
    void returnsPolicyViolationAfterWritingTheReport() throws Exception {
        Path rules = writeRules(
                "archguard.illegal-package-dependency",
                """
                policies:
                        - source: example.web
                          deniedTargets: [example.data]
                """,
                "high");
        Path report = temporaryDirectory.resolve("violation.json");

        Invocation invocation = invoke(repository, rules, report);

        assertEquals(CliExitCode.POLICY_VIOLATION.value(), invocation.exitCode());
        assertTrue(Files.isRegularFile(report));
        assertTrue(new String(Files.readAllBytes(report), StandardCharsets.UTF_8)
                .contains("archguard.illegal-package-dependency"));
    }

    @Test
    void allowsFindingsBelowTheConfiguredFailureThreshold() throws Exception {
        Path rules = writeRules(
                "archguard.illegal-package-dependency",
                """
                policies:
                        - source: example.web
                          deniedTargets: [example.data]
                """,
                "critical");
        Files.writeString(
                rules,
                Files.readString(rules).replace("    parameters:", "    severity: low\n    parameters:"));
        Path report = temporaryDirectory.resolve("allowed.json");

        Invocation invocation = invoke(repository, rules, report);

        assertEquals(CliExitCode.SUCCESS.value(), invocation.exitCode());
        assertTrue(invocation.stdout().contains("findings=1"));
        assertTrue(Files.isRegularFile(report));
    }

    @Test
    void returnsInvalidInputForArgumentsAndRulesWithoutCreatingAReport() throws Exception {
        Invocation arguments = invoke(new String[] {"scan", repository.toString()});
        Path invalidRules = temporaryDirectory.resolve("invalid.yaml");
        Files.writeString(invalidRules, "version: 0.1.0\nunknown: true\n");
        Path report = temporaryDirectory.resolve("invalid.json");
        Invocation rules = invoke(repository, invalidRules, report);

        assertEquals(CliExitCode.INVALID_INPUT.value(), arguments.exitCode());
        assertTrue(arguments.stderr().contains(ScannerCli.USAGE));
        assertEquals(CliExitCode.INVALID_INPUT.value(), rules.exitCode());
        assertFalse(Files.exists(report));
    }

    @Test
    void returnsInvalidInputForMissingRepositoryWithoutLeakingAbsolutePaths() throws Exception {
        Path rules = writeRules("archguard.dependency-cycle", "scope: component", "high");
        Path report = temporaryDirectory.resolve("failure.json");

        Invocation invocation = invoke(temporaryDirectory.resolve("missing"), rules, report);

        assertEquals(CliExitCode.INVALID_INPUT.value(), invocation.exitCode());
        assertFalse(invocation.stderr().contains(temporaryDirectory.toString()));
        assertFalse(Files.exists(report));
    }

    @Test
    void returnsScanFailureWhenOutputLimitIsExceeded() throws Exception {
        Path rules = writeRules("archguard.dependency-cycle", "scope: component", "high");
        String limited = Files.readString(rules).replace("rules:", "limits: {maxOutputBytes: 1024}\nrules:");
        Files.writeString(rules, limited);
        Path report = temporaryDirectory.resolve("too-large.json");
        Files.writeString(report, "existing-report");

        Invocation invocation = invoke(repository, rules, report);

        assertEquals(CliExitCode.SCAN_FAILURE.value(), invocation.exitCode());
        assertTrue(invocation.stderr().contains("report.limit.bytes"));
        assertEquals("existing-report", Files.readString(report));
    }

    @Test
    void returnsScanFailureWithoutReplacingAReportWhenDurationLimitIsExceeded() throws Exception {
        Path rules = writeRules("archguard.dependency-cycle", "scope: component", "high");
        Path report = temporaryDirectory.resolve("timeout.json");
        Files.writeString(report, "existing-report");
        TimedAnalysisRunner timeoutRunner = new TimedAnalysisRunner() {
            @Override
            <T> T run(java.util.function.Supplier<T> analysis, Duration timeout) {
                throw new ScanTimeoutException();
            }
        };

        Invocation invocation = invoke(
                new ScannerCli(
                        new RuleConfigurationLoader(),
                        new io.github.aiarchguard.scanner.parser.java.JavaSourceScanner(),
                        new io.github.aiarchguard.scanner.rule.StructureRuleEngine(),
                        new ReportJson(),
                        new io.github.aiarchguard.scanner.report.ReportWriter(),
                        timeoutRunner),
                repository,
                rules,
                report);

        assertEquals(CliExitCode.SCAN_FAILURE.value(), invocation.exitCode());
        assertTrue(invocation.stderr().contains("scanner.limit.duration"));
        assertEquals("existing-report", Files.readString(report));
    }

    @Test
    void timedRunnerCancelsWorkAtTheConfiguredDeadline() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);

        ScanTimeoutException exception = assertThrows(
                ScanTimeoutException.class,
                () -> new TimedAnalysisRunner().run(
                        () -> {
                            try {
                                Thread.sleep(30_000);
                            } catch (InterruptedException interruptedException) {
                                interrupted.countDown();
                                Thread.currentThread().interrupt();
                            }
                            return "late";
                        },
                        Duration.ofMillis(200)));

        assertEquals("scan exceeded configured duration", exception.getMessage());
        assertTrue(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void writesPartialReportAndReturnsScanFailureForStableDiagnostics() throws Exception {
        Files.writeString(
                repository.resolve("src/main/java/example/web/Controller.java"),
                "package example.web; public class {\n");
        Path rules = writeRules("archguard.dependency-cycle", "scope: component", "high");
        Path report = temporaryDirectory.resolve("diagnostic.json");

        Invocation invocation = invoke(repository, rules, report);

        assertEquals(CliExitCode.SCAN_FAILURE.value(), invocation.exitCode());
        assertTrue(Files.isRegularFile(report));
        assertTrue(invocation.stderr().contains("java.syntax.invalid"));
        assertFalse(invocation.stderr().contains(temporaryDirectory.toString()));
    }

    @Test
    void supportsHelpAndVersion() {
        assertEquals(CliExitCode.SUCCESS.value(), invoke(new String[] {"--help"}).exitCode());
        Invocation version = invoke(new String[] {"--version"});
        assertEquals(CliExitCode.SUCCESS.value(), version.exitCode());
        assertEquals(ScannerCli.VERSION + System.lineSeparator(), version.stdout());
    }

    private Path writeRules(String id, String parameters, String failOn) throws Exception {
        Path rules = temporaryDirectory.resolve("rules-" + Math.abs(id.hashCode()) + ".yaml");
        Files.writeString(
                rules,
                """
                version: 0.1.0
                project:
                  identity: example:application
                  name: Example Application
                failOn: %s
                rules:
                  - id: %s
                    parameters:
                      %s
                """.formatted(failOn, id, parameters.replace("\n", "\n      ")));
        return rules;
    }

    private Invocation invoke(Path root, Path rules, Path output) {
        return invoke(new ScannerCli(), root, rules, output);
    }

    private Invocation invoke(ScannerCli scannerCli, Path root, Path rules, Path output) {
        return invoke(scannerCli, new String[] {
            "scan", root.toString(), "--rules", rules.toString(), "--output", output.toString()
        });
    }

    private Invocation invoke(String[] arguments) {
        return invoke(new ScannerCli(), arguments);
    }

    private Invocation invoke(ScannerCli scannerCli, String[] arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = scannerCli.run(
                arguments,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Invocation(
                exit,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private record Invocation(int exitCode, String stdout, String stderr) {}
}
