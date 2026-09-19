package io.github.aiarchguard.scanner.cli;

import io.github.aiarchguard.scanner.domain.model.Finding;
import io.github.aiarchguard.scanner.parser.java.JavaParseDiagnostic;
import io.github.aiarchguard.scanner.parser.java.JavaScanException;
import io.github.aiarchguard.scanner.parser.java.JavaScanRequest;
import io.github.aiarchguard.scanner.parser.java.JavaScanResult;
import io.github.aiarchguard.scanner.parser.java.JavaSourceScanner;
import io.github.aiarchguard.scanner.report.DomainReport;
import io.github.aiarchguard.scanner.report.ReportJson;
import io.github.aiarchguard.scanner.report.ReportValidationException;
import io.github.aiarchguard.scanner.report.ReportWriter;
import io.github.aiarchguard.scanner.rule.RuleDiagnostic;
import io.github.aiarchguard.scanner.rule.RuleEngineException;
import io.github.aiarchguard.scanner.rule.RuleEngineResult;
import io.github.aiarchguard.scanner.rule.RuleInput;
import io.github.aiarchguard.scanner.rule.StructureRuleEngine;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ScannerCli {

    static final String USAGE = "Usage: archguard scan <repository> --rules <rules.yaml> --output <report.json>";
    static final String VERSION = "0.2.0";

    private final RuleConfigurationLoader configurationLoader;
    private final JavaSourceScanner sourceScanner;
    private final StructureRuleEngine ruleEngine;
    private final ReportJson reportJson;
    private final ReportWriter reportWriter;
    private final TimedAnalysisRunner timedAnalysisRunner;

    public ScannerCli() {
        this(
                new RuleConfigurationLoader(),
                new JavaSourceScanner(),
                new StructureRuleEngine(),
                new ReportJson(),
                new ReportWriter(),
                new TimedAnalysisRunner());
    }

    ScannerCli(
            RuleConfigurationLoader configurationLoader,
            JavaSourceScanner sourceScanner,
            StructureRuleEngine ruleEngine,
            ReportJson reportJson,
            ReportWriter reportWriter) {
        this(configurationLoader, sourceScanner, ruleEngine, reportJson, reportWriter, new TimedAnalysisRunner());
    }

    ScannerCli(
            RuleConfigurationLoader configurationLoader,
            JavaSourceScanner sourceScanner,
            StructureRuleEngine ruleEngine,
            ReportJson reportJson,
            ReportWriter reportWriter,
            TimedAnalysisRunner timedAnalysisRunner) {
        this.configurationLoader = configurationLoader;
        this.sourceScanner = sourceScanner;
        this.ruleEngine = ruleEngine;
        this.reportJson = reportJson;
        this.reportWriter = reportWriter;
        this.timedAnalysisRunner = timedAnalysisRunner;
    }

    public int run(String[] arguments, PrintStream standardOutput, PrintStream standardError) {
        if (standardOutput == null || standardError == null) {
            throw new IllegalArgumentException("CLI output streams must not be null");
        }
        if (arguments != null && arguments.length == 1 && ("--help".equals(arguments[0]) || "help".equals(arguments[0]))) {
            standardOutput.println(USAGE);
            return CliExitCode.SUCCESS.value();
        }
        if (arguments != null && arguments.length == 1 && "--version".equals(arguments[0])) {
            standardOutput.println(VERSION);
            return CliExitCode.SUCCESS.value();
        }

        CliArguments parsed;
        ScannerConfiguration configuration;
        try {
            parsed = CliArguments.parse(arguments);
            configuration = configurationLoader.load(parsed.rules());
        } catch (CliUsageException exception) {
            standardError.println("ERROR cli.input.invalid " + safeMessage(exception));
            standardError.println(USAGE);
            return CliExitCode.INVALID_INPUT.value();
        } catch (IllegalArgumentException exception) {
            standardError.println("ERROR cli.input.invalid command line contains an invalid path or value");
            standardError.println(USAGE);
            return CliExitCode.INVALID_INPUT.value();
        } catch (ConfigurationException exception) {
            standardError.println("ERROR " + exception.code() + " " + exception.getMessage());
            return CliExitCode.INVALID_INPUT.value();
        }

        try {
            ScanOutput output = timedAnalysisRunner.run(
                    () -> analyze(parsed, configuration), Duration.ofSeconds(configuration.maxDurationSeconds()));
            if (output.json().length > configuration.maxOutputBytes()) {
                standardError.println("ERROR report.limit.bytes report exceeds the configured output limit");
                return CliExitCode.SCAN_FAILURE.value();
            }
            reportWriter.write(parsed.output(), output.json());
            renderDiagnostics(output.scan().diagnostics(), output.evaluated().diagnostics(), standardError);
            standardOutput.printf(
                    Locale.ROOT,
                    "ArchGuard scan complete: findings=%d diagnostics=%d%n",
                    output.evaluated().findings().size(),
                    output.scan().diagnostics().size() + output.evaluated().diagnostics().size());
            if (!output.scan().diagnostics().isEmpty() || !output.evaluated().diagnostics().isEmpty()) {
                return CliExitCode.SCAN_FAILURE.value();
            }
            return hasBlockingFinding(output.evaluated().findings(), configuration)
                    ? CliExitCode.POLICY_VIOLATION.value()
                    : CliExitCode.SUCCESS.value();
        } catch (ScanTimeoutException exception) {
            standardError.println("ERROR scanner.limit.duration " + exception.getMessage());
            return CliExitCode.SCAN_FAILURE.value();
        } catch (JavaScanException exception) {
            standardError.println("ERROR " + exception.code() + " " + exception.getMessage());
            return isInvalidRepositoryInput(exception.code())
                    ? CliExitCode.INVALID_INPUT.value()
                    : CliExitCode.SCAN_FAILURE.value();
        } catch (RuleEngineException exception) {
            standardError.println("ERROR " + exception.code() + " " + exception.getMessage());
            return CliExitCode.SCAN_FAILURE.value();
        } catch (ReportValidationException exception) {
            standardError.println("ERROR report.validation.failed scanner report failed validation");
            return CliExitCode.SCAN_FAILURE.value();
        } catch (IOException exception) {
            standardError.println("ERROR report.write.failed scanner report could not be written atomically");
            return CliExitCode.SCAN_FAILURE.value();
        } catch (RuntimeException exception) {
            standardError.println("ERROR scanner.internal scanner failed without exposing host details");
            return CliExitCode.SCAN_FAILURE.value();
        }
    }

    private ScanOutput analyze(CliArguments parsed, ScannerConfiguration configuration) {
        JavaScanResult scan = sourceScanner.scan(new JavaScanRequest(
                parsed.repository(),
                configuration.projectIdentity(),
                configuration.projectName(),
                "0.1.0",
                configuration.scanLimits()));
        RuleInput input = new RuleInput(
                "0.1.0",
                scan.project(),
                scan.artifacts(),
                scan.components(),
                scan.dependencies(),
                scan.metrics(),
                scan.evidences());
        RuleEngineResult evaluated = ruleEngine.execute(input, configuration.rules(), configuration.ruleLimits());
        DomainReport report = new DomainReport(
                scan.project(),
                scan.artifacts(),
                scan.components(),
                scan.dependencies(),
                scan.metrics(),
                evaluated.findings(),
                scan.evidences());
        return new ScanOutput(scan, evaluated, reportJson.serialize(report));
    }

    private static boolean hasBlockingFinding(List<Finding> findings, ScannerConfiguration configuration) {
        return findings.stream().anyMatch(finding -> finding.severity().ordinal() >= configuration.failOn().ordinal());
    }

    private static boolean isInvalidRepositoryInput(String code) {
        return "java.path.invalid-root".equals(code) || "java.path.root-symlink".equals(code);
    }

    private static void renderDiagnostics(
            List<JavaParseDiagnostic> parseDiagnostics,
            List<RuleDiagnostic> ruleDiagnostics,
            PrintStream standardError) {
        parseDiagnostics.stream().sorted().forEach(diagnostic -> standardError.printf(
                Locale.ROOT,
                "ERROR %s %s:%d:%d %s%n",
                diagnostic.code(),
                diagnostic.path(),
                diagnostic.line(),
                diagnostic.column(),
                diagnostic.message()));
        ruleDiagnostics.stream()
                .sorted(Comparator.naturalOrder())
                .forEach(diagnostic -> standardError.printf(
                        Locale.ROOT,
                        "ERROR %s %s %s%n",
                        diagnostic.code(),
                        diagnostic.subjectId(),
                        diagnostic.message()));
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "invalid command line" : message;
    }

    private record ScanOutput(JavaScanResult scan, RuleEngineResult evaluated, byte[] json) {}
}
