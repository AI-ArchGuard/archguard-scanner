package io.github.aiarchguard.scanner.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.ArtifactKind;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.ComponentKind;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.DependencyKind;
import io.github.aiarchguard.scanner.domain.model.EntityKind;
import io.github.aiarchguard.scanner.domain.model.Evidence;
import io.github.aiarchguard.scanner.domain.model.EvidenceKind;
import io.github.aiarchguard.scanner.domain.model.Finding;
import io.github.aiarchguard.scanner.domain.model.Metric;
import io.github.aiarchguard.scanner.domain.model.Project;
import io.github.aiarchguard.scanner.domain.model.Severity;
import io.github.aiarchguard.scanner.domain.model.SourceLocation;
import io.github.aiarchguard.scanner.domain.model.StableIdGenerator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class S5RuleEngineTest {

    private final StructureRuleEngine engine = new StructureRuleEngine();

    @Test
    void reportsOnlyDirectOfficialSpringControllerRepositoryAccess() {
        Fixture fixture = new Fixture();
        fixture.type("Controller", "web", "com.app.web", "org.springframework.web.bind.annotation.RestController");
        fixture.type("Service", "service", "com.app.service", "org.springframework.stereotype.Service");
        fixture.type("Repository", "data", "com.app.data", "org.springframework.stereotype.Repository");
        fixture.type("CustomController", "web", "com.app.custom", "com.app.custom.Controller");
        fixture.link("direct", "Controller", "Repository");
        fixture.link("via-service", "Controller", "Service");
        fixture.link("service-repository", "Service", "Repository");
        fixture.link("custom", "CustomController", "Repository");

        List<Finding> findings = execute(fixture, new ControllerRepositoryAccessConfig()).findings();

        assertEquals(1, findings.size());
        assertEquals(fixture.dependencies.get("direct").id(), findings.getFirst().subjectId());
        assertEquals(Severity.HIGH, findings.getFirst().severity());
    }

    @Test
    void enforcesExplicitModulePublicApiAndDiagnosesMissingBoundary() {
        Fixture fixture = new Fixture();
        fixture.type("Caller", "app", "com.app");
        fixture.type("Api", "orders", "com.orders.api");
        fixture.type("Internal", "orders", "com.orders.internal");
        fixture.type("Unknown", "unknown", "com.unknown.internal");
        fixture.link("api", "Caller", "Api");
        fixture.link("internal", "Caller", "Internal");
        fixture.link("unknown", "Caller", "Unknown");

        RuleEngineResult result = execute(
                fixture,
                new InternalModuleAccessConfig(List.of(
                        new ModuleBoundary("orders", List.of(new PackageSelector("com.orders.api.**"))),
                        new ModuleBoundary("app", List.of(new PackageSelector("com.app.**"))))));

        assertEquals(1, result.findings().size());
        assertEquals(fixture.dependencies.get("internal").id(), result.findings().getFirst().subjectId());
        assertEquals(1, result.diagnostics().size());
        assertEquals("rule.module-boundary.missing", result.diagnostics().getFirst().code());
    }

    @Test
    void matchesForbiddenTypePackageAndMavenCoordinatesWithoutGuessing() {
        Fixture fixture = new Fixture();
        fixture.type("Caller", "app", "com.app");
        fixture.type("Secret", "target", "com.secret.internal");
        fixture.module("external", "org.bad:danger:2.0.0", false);
        fixture.module("external-versioned", "org.bad:legacy:1.4.0", false);
        fixture.moduleWithUnresolvedVersion("external-no-version", "org.bad:maybe");
        fixture.module("unresolved", null, true);
        fixture.link("type", "Caller", "Secret");
        fixture.link("external", "Caller", "external");
        fixture.link("external-versioned", "Caller", "external-versioned");
        fixture.link("external-no-version", "Caller", "external-no-version");
        fixture.link("unresolved", "Caller", "unresolved");

        RuleEngineResult result = execute(
                fixture,
                new ForbiddenComponentConfig(List.of(
                        new ForbiddenTypeSelector("com.secret.internal.Secret"),
                        new ForbiddenPackageSelector(new PackageSelector("com.secret.**")),
                        new ForbiddenMavenCoordinateSelector("org.bad:danger"),
                        new ForbiddenMavenCoordinateSelector("org.bad:legacy:1.4.0"),
                        new ForbiddenMavenCoordinateSelector("org.bad:maybe:9.9.9"))));

        assertEquals(3, result.findings().size());
        assertEquals(2, result.diagnostics().size());
        assertEquals(
                Set.of("rule.maven-coordinate.unresolved", "rule.maven-version.unresolved"),
                result.diagnostics().stream().map(RuleDiagnostic::code).collect(java.util.stream.Collectors.toSet()));
        assertTrue(execute(
                        fixture,
                        new IllegalPackageDependencyConfig(
                                List.of(new PackagePolicy(
                                        new PackageSelector("com.app"),
                                        List.of(),
                                        List.of(new PackageSelector("com.forbidden")))),
                                Severity.HIGH))
                .findings()
                .isEmpty());
    }

    @Test
    void reportsComplexityOnlyAboveConfiguredBoundaries() {
        Fixture fixture = new Fixture();
        fixture.type("Exact", "app", "com.app");
        fixture.type("TypeHigh", "app", "com.app");
        fixture.function("High", "app", "com.app.High#run()", "com.app", "method");
        fixture.metric("Exact", 5);
        fixture.metric("TypeHigh", 6);
        fixture.metric("High", 6);
        fixture.moduleMetric("app", 7);

        List<Finding> findings = execute(fixture, new ComplexityThresholdConfig(5, 5, 6)).findings();

        assertEquals(3, findings.size());
        assertTrue(findings.stream().anyMatch(finding -> finding.subjectId().equals(fixture.components.get("High").id())));
        assertTrue(findings.stream().anyMatch(finding -> finding.subjectId().equals(fixture.components.get("TypeHigh").id())));
        assertTrue(findings.stream().anyMatch(finding -> finding.subjectId().equals(fixture.artifacts.get("app").id())));
        assertTrue(findings.stream().allMatch(finding -> finding.severity() == Severity.MEDIUM));
    }

    @Test
    void requiresAnnotationsOnlyForApplicableResolvedDeclarations() {
        Fixture fixture = new Fixture();
        fixture.type("Present", "app", "com.app", "com.arch.Aggregate");
        fixture.type("Missing", "app", "com.app");
        fixture.typeWithResolution("Unknown", "app", "com.app", "incomplete");
        fixture.function("Method", "app", "com.app.Present#run()", "com.app", "method");
        fixture.function("Ctor", "app", "com.app.Present#<init>()", "com.app", "constructor", "com.arch.Command");
        AnnotationRequirement requirement = new AnnotationRequirement(
                "com.arch.Aggregate",
                Set.of(ComponentKind.TYPE),
                List.of("class"),
                List.of(new PackageSelector("com.app.**")));
        AnnotationRequirement functionRequirement = new AnnotationRequirement(
                "com.arch.Command",
                Set.of(ComponentKind.FUNCTION),
                List.of("method", "constructor"),
                List.of(new PackageSelector("com.app.**")));

        RuleEngineResult result = execute(
                fixture, new RequiredAnnotationConfig(List.of(requirement, functionRequirement)));

        assertEquals(2, result.findings().size());
        assertTrue(result.findings().stream()
                .anyMatch(finding -> finding.subjectId().equals(fixture.components.get("Missing").id())));
        assertTrue(result.findings().stream()
                .anyMatch(finding -> finding.subjectId().equals(fixture.components.get("Method").id())));
        assertEquals(1, result.diagnostics().size());
        assertEquals(fixture.components.get("Unknown").id(), result.diagnostics().getFirst().subjectId());
    }

    @Test
    void rejectsInfoSeverityAndProducesStableOutputForReorderedInput() {
        assertThrows(IllegalArgumentException.class, () -> new ControllerRepositoryAccessConfig(Severity.INFO));
        Fixture fixture = new Fixture();
        fixture.type("Controller", "web", "com.web", "org.springframework.stereotype.Controller");
        fixture.type("Repository", "data", "com.data", "org.springframework.stereotype.Repository");
        fixture.link("direct", "Controller", "Repository");
        RuleInput ordered = fixture.input();
        List<Artifact> artifacts = reversed(ordered.artifacts());
        List<Component> components = reversed(ordered.components());
        List<Dependency> dependencies = reversed(ordered.dependencies());
        List<Metric> metrics = reversed(ordered.metrics());
        List<Evidence> evidences = reversed(ordered.evidences());
        RuleInput reordered = new RuleInput(
                ordered.schemaVersion(), ordered.project(), artifacts, components, dependencies, metrics, evidences);

        RuleEngineResult first = engine.execute(
                ordered, List.of(new ControllerRepositoryAccessConfig()), RuleExecutionLimits.defaults());
        RuleEngineResult second = engine.execute(
                reordered, List.of(new ControllerRepositoryAccessConfig()), RuleExecutionLimits.defaults());

        assertEquals(first, second);
    }

    private RuleEngineResult execute(Fixture fixture, StructureRuleConfiguration config) {
        return engine.execute(fixture.input(), List.of(config), RuleExecutionLimits.defaults());
    }

    private static <T> List<T> reversed(List<T> values) {
        List<T> result = new ArrayList<>(values);
        Collections.reverse(result);
        return result;
    }

    private static final class Fixture {
        private static final String VERSION = "0.1.0";
        private static final String IDENTITY = "example/s5";
        private final Project project = new Project(id(EntityKind.PROJECT, ".", IDENTITY), IDENTITY, "s5", "java", ".", Map.of());
        private final Map<String, Artifact> artifacts = new LinkedHashMap<>();
        private final Map<String, Component> components = new LinkedHashMap<>();
        private final Map<String, Dependency> dependencies = new LinkedHashMap<>();
        private final Map<String, Evidence> evidences = new LinkedHashMap<>();
        private final Map<String, Metric> metrics = new LinkedHashMap<>();

        void type(String key, String module, String packageName, String... annotations) {
            Artifact artifact = artifact(module, "example:" + module + ":1.0.0", false);
            String qualifiedName = packageName + "." + key;
            SourceLocation location = new SourceLocation(module + "/src/main/java/" + key + ".java", 1, 1, 1, 20);
            String componentId = id(EntityKind.COMPONENT, location.path(), qualifiedName);
            String evidenceId = declaration(componentId, qualifiedName, location);
            Map<String, List<String>> extensions = new LinkedHashMap<>();
            extensions.put("java.package", List.of(packageName));
            extensions.put("java.declaration-kind", List.of("class"));
            extensions.put("java.annotation-resolution", List.of("complete"));
            extensions.put("archguard.declaration-evidence-id", List.of(evidenceId));
            if (annotations.length > 0) extensions.put("java.annotations", List.of(annotations));
            components.put(key, new Component(
                    componentId, artifact.id(), ComponentKind.TYPE, key, "java", qualifiedName, location, extensions));
        }

        void typeWithResolution(String key, String module, String packageName, String resolution) {
            type(key, module, packageName);
            Component original = components.get(key);
            Map<String, List<String>> extensions = new LinkedHashMap<>(original.extensions());
            extensions.put("java.annotation-resolution", List.of(resolution));
            components.put(key, new Component(
                    original.id(), original.artifactId(), original.kind(), original.name(), original.language(),
                    original.qualifiedName(), original.location(), extensions));
        }

        void function(
                String key,
                String module,
                String qualifiedName,
                String packageName,
                String declarationKind,
                String... annotations) {
            Artifact artifact = artifact(module, "example:" + module + ":1.0.0", false);
            SourceLocation location = new SourceLocation(module + "/src/main/java/Functions.java", 2, 1, 4, 2);
            String componentId = id(EntityKind.COMPONENT, location.path(), qualifiedName);
            String evidenceId = declaration(componentId, qualifiedName, location);
            Map<String, List<String>> extensions = new LinkedHashMap<>();
            extensions.put("java.package", List.of(packageName));
            extensions.put("java.declaration-kind", List.of(declarationKind));
            extensions.put("java.annotation-resolution", List.of("complete"));
            extensions.put("archguard.declaration-evidence-id", List.of(evidenceId));
            if (annotations.length > 0) extensions.put("java.annotations", List.of(annotations));
            components.put(key, new Component(
                    componentId,
                    artifact.id(),
                    ComponentKind.FUNCTION,
                    key,
                    "java",
                    qualifiedName,
                    location,
                    extensions));
        }

        void module(String key, String coordinate, boolean unresolved) {
            Artifact artifact = artifact(key, coordinate, unresolved);
            components.put(key, new Component(
                    id(EntityKind.COMPONENT, key + "/pom.xml", key), artifact.id(), ComponentKind.MODULE,
                    key, "java", coordinate == null ? key : coordinate,
                    new SourceLocation(key + "/pom.xml", 1, 1, 1, 1), Map.of()));
        }

        void moduleWithUnresolvedVersion(String key, String coordinate) {
            Artifact original = artifact(key, coordinate, false);
            Map<String, List<String>> extensions = new LinkedHashMap<>(original.extensions());
            extensions.put("maven.version-unresolved", List.of("true"));
            Artifact updated = new Artifact(
                    original.id(), original.projectId(), original.kind(), original.name(), original.language(),
                    original.repositoryPath(), original.qualifiedName(), extensions);
            artifacts.put(key, updated);
            components.put(key, new Component(
                    id(EntityKind.COMPONENT, key + "/pom.xml", key), updated.id(), ComponentKind.MODULE,
                    key, "java", coordinate, new SourceLocation(key + "/pom.xml", 1, 1, 1, 1), Map.of()));
        }

        void link(String key, String sourceKey, String targetKey) {
            Component source = components.get(sourceKey);
            Component target = components.get(targetKey);
            SourceLocation location = source.location();
            String evidenceId = id(EntityKind.EVIDENCE, location.path(), "link|" + key);
            evidences.put(evidenceId, new Evidence(
                    evidenceId, EvidenceKind.REFERENCE, "reference " + key, location, Map.of()));
            dependencies.put(key, new Dependency(
                    id(EntityKind.DEPENDENCY, location.path(), key), source.id(), target.id(),
                    DependencyKind.DEPENDS_ON, List.of(evidenceId), Map.of()));
        }

        void metric(String componentKey, int value) {
            Component component = components.get(componentKey);
            String evidenceId = id(EntityKind.EVIDENCE, component.location().path(), "metric|" + componentKey);
            evidences.put(evidenceId, new Evidence(
                    evidenceId, EvidenceKind.METRIC, "complexity " + value, component.location(), Map.of()));
            Metric metric = new Metric(
                    id(EntityKind.METRIC, component.location().path(), componentKey),
                    component.id(),
                    "complexity.cyclomatic",
                    BigDecimal.valueOf(value),
                    "count",
                    Map.of("archguard.evidence-id", List.of(evidenceId)));
            metrics.put(componentKey, metric);
        }

        void moduleMetric(String module, int value) {
            Artifact artifact = artifacts.get(module);
            SourceLocation location = new SourceLocation(module + "/pom.xml", 1, 1, 1, 1);
            String evidenceId = id(EntityKind.EVIDENCE, location.path(), "metric|module|" + module);
            evidences.put(evidenceId, new Evidence(
                    evidenceId, EvidenceKind.METRIC, "module complexity " + value, location, Map.of()));
            metrics.put("module:" + module, new Metric(
                    id(EntityKind.METRIC, location.path(), "module|" + module),
                    artifact.id(),
                    "complexity.cyclomatic",
                    BigDecimal.valueOf(value),
                    "count",
                    Map.of("archguard.evidence-id", List.of(evidenceId))));
        }

        RuleInput input() {
            return new RuleInput(
                    VERSION, project, List.copyOf(artifacts.values()), List.copyOf(components.values()),
                    List.copyOf(dependencies.values()), List.copyOf(metrics.values()), List.copyOf(evidences.values()));
        }

        private Artifact artifact(String module, String coordinate, boolean unresolved) {
            return artifacts.computeIfAbsent(module, ignored -> {
                Map<String, List<String>> extensions = new LinkedHashMap<>();
                if (coordinate != null) extensions.put("maven.coordinate", List.of(coordinate));
                if (unresolved) extensions.put("maven.coordinate-unresolved", List.of("true"));
                return new Artifact(
                        id(EntityKind.ARTIFACT, module, module), project.id(), ArtifactKind.MODULE,
                        module, "java", module, module, extensions);
            });
        }

        private String declaration(String componentId, String qualifiedName, SourceLocation location) {
            String evidenceId = id(EntityKind.EVIDENCE, location.path(), qualifiedName + "|declaration");
            evidences.put(evidenceId, new Evidence(
                    evidenceId, EvidenceKind.DECLARATION, "Declaration of " + qualifiedName, location, Map.of()));
            return evidenceId;
        }

        private static String id(EntityKind kind, String path, String qualifiedName) {
            return StableIdGenerator.generate(VERSION, IDENTITY, kind, "java", path, qualifiedName);
        }
    }
}
