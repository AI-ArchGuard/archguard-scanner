package io.github.aiarchguard.scanner.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aiarchguard.scanner.domain.model.Severity;
import io.github.aiarchguard.scanner.rule.ComplexityThresholdConfig;
import io.github.aiarchguard.scanner.rule.ControllerRepositoryAccessConfig;
import io.github.aiarchguard.scanner.rule.DependencyCycleConfig;
import io.github.aiarchguard.scanner.rule.ForbiddenComponentConfig;
import io.github.aiarchguard.scanner.rule.IllegalPackageDependencyConfig;
import io.github.aiarchguard.scanner.rule.InternalModuleAccessConfig;
import io.github.aiarchguard.scanner.rule.LayeredArchitectureConfig;
import io.github.aiarchguard.scanner.rule.RequiredAnnotationConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleConfigurationLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsAllEightRulesIntoStronglyTypedConfigurations() throws Exception {
        ScannerConfiguration configuration = load(fullConfiguration());

        assertEquals("example:application", configuration.projectIdentity());
        assertEquals("Example Application", configuration.projectName());
        assertEquals(Severity.CRITICAL, configuration.failOn());
        assertEquals(1234, configuration.scanLimits().maxFiles());
        assertEquals(4096, configuration.maxOutputBytes());
        assertEquals(8, configuration.rules().size());
        List<Class<?>> types = configuration.rules().stream().map(Object::getClass).toList();
        assertTrue(types.containsAll(List.of(
                IllegalPackageDependencyConfig.class,
                LayeredArchitectureConfig.class,
                DependencyCycleConfig.class,
                ControllerRepositoryAccessConfig.class,
                InternalModuleAccessConfig.class,
                ForbiddenComponentConfig.class,
                ComplexityThresholdConfig.class,
                RequiredAnnotationConfig.class)));
        assertTrue(configuration.rules().stream().anyMatch(ComplexityThresholdConfig.class::isInstance));
    }

    @Test
    void appliesDocumentedDefaults() throws Exception {
        ScannerConfiguration configuration = load(minimalConfiguration("archguard.dependency-cycle", "scope: package"));

        assertEquals(Severity.HIGH, configuration.failOn());
        assertEquals(Severity.HIGH, configuration.rules().getFirst().severity());
        assertEquals(10_000, configuration.scanLimits().maxFiles());
        assertEquals(50L * 1024 * 1024, configuration.maxOutputBytes());
    }

    @Test
    void rejectsUnknownKeysRulesAndDuplicateRuleIds() {
        assertInvalid(minimalConfiguration("archguard.dependency-cycle", "scope: package") + "unknown: true\n");
        assertInvalid(minimalConfiguration("archguard.unknown", "scope: package"));
        String duplicate = """
                version: 0.1.0
                project:
                  identity: example
                  name: Example
                rules:
                  - id: archguard.dependency-cycle
                    parameters: {scope: package}
                  - id: archguard.dependency-cycle
                    parameters: {scope: module}
                """;
        assertInvalid(duplicate);
    }

    @Test
    void rejectsDuplicateKeysMultipleDocumentsAndYamlReferences() {
        assertInvalid("""
                version: 0.1.0
                version: 0.1.0
                project: {identity: example, name: Example}
                rules:
                  - id: archguard.dependency-cycle
                    parameters: {scope: package}
                """);
        assertInvalid(minimalConfiguration("archguard.dependency-cycle", "scope: package") + "---\n{}\n");
        assertInvalid("""
                version: 0.1.0
                project: &project
                  identity: example
                  name: Example
                rules:
                  - id: archguard.dependency-cycle
                    parameters: {scope: package}
                """);
        assertInvalid("""
                version: 0.1.0
                project: !unsafe
                  identity: example
                  name: Example
                rules:
                  - id: archguard.dependency-cycle
                    parameters: {scope: package}
                """);
        assertInvalid("""
                version: 0.1.0
                project: !!map
                  identity: example
                  name: Example
                rules:
                  - id: archguard.dependency-cycle
                    parameters: {scope: package}
                """);
    }

    @Test
    void rejectsInvalidSeverityLimitAndSelector() {
        assertInvalid(minimalConfiguration("archguard.dependency-cycle", "scope: package").replace(
                "rules:", "failOn: info\nrules:"));
        assertInvalid(minimalConfiguration("archguard.dependency-cycle", "scope: package").replace(
                "rules:", "limits: {maxDepth: 3}\nrules:"));
        assertInvalid(minimalConfiguration(
                "archguard.illegal-package-dependency",
                """
                policies:
                      - source: invalid package
                        deniedTargets: [example.data]
                """));
    }

    @Test
    void rejectsNonUtf8Input() throws Exception {
        Path rules = temporaryDirectory.resolve("invalid-encoding.yaml");
        Files.write(rules, new byte[] {(byte) 0xC3, 0x28});

        ConfigurationException exception =
                assertThrows(ConfigurationException.class, () -> new RuleConfigurationLoader().load(rules));

        assertEquals("config.encoding.invalid", exception.code());
    }

    @Test
    void exposesAValidBundledSchema() {
        String schema = new RuleConfigurationLoader().schemaText();

        assertTrue(schema.contains("ArchGuard Scanner Rules 0.1.0"));
        assertTrue(schema.contains("additionalProperties"));
    }

    private ScannerConfiguration load(String yaml) throws Exception {
        Path rules = temporaryDirectory.resolve("rules-" + Math.abs(yaml.hashCode()) + ".yaml");
        Files.writeString(rules, yaml);
        return new RuleConfigurationLoader().load(rules);
    }

    private void assertInvalid(String yaml) {
        assertThrows(ConfigurationException.class, () -> load(yaml));
    }

    private static String minimalConfiguration(String ruleId, String parameters) {
        return """
                version: 0.1.0
                project:
                  identity: example:application
                  name: Example Application
                rules:
                  - id: %s
                    parameters:
                      %s
                """.formatted(ruleId, parameters.replace("\n", "\n      "));
    }

    private static String fullConfiguration() {
        return """
                version: 0.1.0
                project:
                  identity: example:application
                  name: Example Application
                failOn: critical
                limits:
                  maxFiles: 1234
                  maxFileBytes: 2048
                  maxTotalBytes: 8192
                  maxDepth: 24
                  maxNodes: 2000
                  maxEdges: 3000
                  maxFindings: 400
                  maxOutputBytes: 4096
                rules:
                  - id: archguard.illegal-package-dependency
                    severity: high
                    parameters:
                      policies:
                        - source: example.web.**
                          allowedTargets: [example.service.**]
                          deniedTargets: [example.data.**]
                  - id: archguard.layered-architecture
                    parameters:
                      allowLayerSkipping: false
                      layers:
                        - name: web
                          packages: [example.web.**]
                        - name: service
                          packages: [example.service.**]
                  - id: archguard.dependency-cycle
                    parameters:
                      scope: component
                  - id: spring.controller-repository-access
                    parameters: {}
                  - id: archguard.internal-module-access
                    parameters:
                      boundaries:
                        - modulePath: module-a
                          publicPackages: [example.api.**]
                  - id: archguard.forbidden-component
                    parameters:
                      selectors:
                        - type: java.lang.Runtime
                        - package: example.legacy.**
                        - maven: bad:library:1.0
                  - id: archguard.complexity-threshold
                    severity: medium
                    parameters:
                      functionThreshold: 10
                      typeThreshold: 20
                      moduleThreshold: 30
                  - id: archguard.required-annotation
                    parameters:
                      requirements:
                        - annotation: example.ArchitectureBoundary
                          componentKinds: [type, function]
                          declarationKinds: [class, method]
                          packages: [example.api.**]
                """;
    }
}
