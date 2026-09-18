package io.github.aiarchguard.scanner.parser.java;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.Evidence;
import io.github.aiarchguard.scanner.domain.model.Metric;
import io.github.aiarchguard.scanner.domain.model.Project;
import java.util.Comparator;
import java.util.List;

public record JavaScanResult(
        Project project,
        List<Artifact> artifacts,
        List<Component> components,
        List<Dependency> dependencies,
        List<Metric> metrics,
        List<Evidence> evidences,
        List<JavaParseDiagnostic> diagnostics) {

    public JavaScanResult {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }
        artifacts = sorted(artifacts, Comparator.comparing(Artifact::id), "artifacts");
        components = sorted(components, Comparator.comparing(Component::id), "components");
        dependencies = sorted(dependencies, Comparator.comparing(Dependency::id), "dependencies");
        metrics = sorted(metrics, Comparator.comparing(Metric::id), "metrics");
        evidences = sorted(evidences, Comparator.comparing(Evidence::id), "evidences");
        diagnostics = sorted(diagnostics, Comparator.naturalOrder(), "diagnostics");
    }

    public JavaScanResult(
            Project project,
            List<Artifact> artifacts,
            List<Component> components,
            List<Dependency> dependencies,
            List<Evidence> evidences,
            List<JavaParseDiagnostic> diagnostics) {
        this(project, artifacts, components, dependencies, List.of(), evidences, diagnostics);
    }

    private static <T> List<T> sorted(List<T> values, Comparator<? super T> comparator, String field) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        return values.stream().sorted(comparator).toList();
    }
}
