package io.github.aiarchguard.scanner.report;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.Evidence;
import io.github.aiarchguard.scanner.domain.model.Finding;
import io.github.aiarchguard.scanner.domain.model.Metric;
import io.github.aiarchguard.scanner.domain.model.Project;
import java.util.List;

public record DomainReport(
        Project project,
        List<Artifact> artifacts,
        List<Component> components,
        List<Dependency> dependencies,
        List<Metric> metrics,
        List<Finding> findings,
        List<Evidence> evidences) {

    public DomainReport {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }
        artifacts = immutable(artifacts, "artifacts");
        components = immutable(components, "components");
        dependencies = immutable(dependencies, "dependencies");
        metrics = immutable(metrics, "metrics");
        findings = immutable(findings, "findings");
        evidences = immutable(evidences, "evidences");
    }

    private static <T> List<T> immutable(List<T> values, String field) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        return List.copyOf(values);
    }
}
