package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.Evidence;
import io.github.aiarchguard.scanner.domain.model.Metric;
import io.github.aiarchguard.scanner.domain.model.Project;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record RuleInput(
        String schemaVersion,
        Project project,
        List<Artifact> artifacts,
        List<Component> components,
        List<Dependency> dependencies,
        List<Metric> metrics,
        List<Evidence> evidences) {

    public RuleInput {
        if (!"0.1.0".equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be 0.1.0");
        }
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }
        artifacts = sorted(artifacts, Artifact::id, "artifacts");
        components = sorted(components, Component::id, "components");
        dependencies = sorted(dependencies, Dependency::id, "dependencies");
        metrics = sorted(metrics, Metric::id, "metrics");
        evidences = sorted(evidences, Evidence::id, "evidences");

        Map<String, Artifact> artifactIndex = unique(artifacts, Artifact::id, "rule.input.duplicate-artifact");
        for (Artifact artifact : artifacts) {
            if (!project.id().equals(artifact.projectId())) {
                throw new RuleEngineException(
                        "rule.input.dangling-artifact", "artifact does not reference the supplied project");
            }
        }
        Map<String, Component> componentIndex = unique(
                components, Component::id, "rule.input.duplicate-component");
        for (Component component : components) {
            if (!artifactIndex.containsKey(component.artifactId())) {
                throw new RuleEngineException(
                        "rule.input.dangling-component", "component references an unknown artifact");
            }
        }
        Map<String, Evidence> evidenceIndex = unique(evidences, Evidence::id, "rule.input.duplicate-evidence");
        Map<String, Dependency> dependencyIndex =
                unique(dependencies, Dependency::id, "rule.input.duplicate-dependency");
        unique(metrics, Metric::id, "rule.input.duplicate-metric");
        for (Metric metric : metrics) {
            if (!project.id().equals(metric.scopeId())
                    && !artifactIndex.containsKey(metric.scopeId())
                    && !componentIndex.containsKey(metric.scopeId())
                    && !dependencyIndex.containsKey(metric.scopeId())) {
                throw new RuleEngineException(
                        "rule.input.dangling-metric", "metric references an unknown supported scope");
            }
        }
        for (Dependency dependency : dependencies) {
            if (!componentIndex.containsKey(dependency.sourceId())
                    || !componentIndex.containsKey(dependency.targetId())) {
                throw new RuleEngineException(
                        "rule.input.dangling-dependency", "dependency references an unknown component");
            }
            if (dependency.evidenceIds().stream().anyMatch(id -> !evidenceIndex.containsKey(id))) {
                throw new RuleEngineException(
                        "rule.input.missing-evidence", "dependency references unknown evidence");
            }
        }
    }

    public RuleInput(
            String schemaVersion,
            Project project,
            List<Artifact> artifacts,
            List<Component> components,
            List<Dependency> dependencies,
            List<Evidence> evidences) {
        this(schemaVersion, project, artifacts, components, dependencies, List.of(), evidences);
    }

    Map<String, Artifact> artifactIndex() {
        return unique(artifacts, Artifact::id, "rule.input.duplicate-artifact");
    }

    Map<String, Component> componentIndex() {
        return unique(components, Component::id, "rule.input.duplicate-component");
    }

    Map<String, Evidence> evidenceIndex() {
        return unique(evidences, Evidence::id, "rule.input.duplicate-evidence");
    }

    Map<String, Metric> metricIndex() {
        return unique(metrics, Metric::id, "rule.input.duplicate-metric");
    }

    private static <T> List<T> sorted(List<T> values, Function<T, String> id, String field) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        return values.stream().sorted(Comparator.comparing(id)).toList();
    }

    private static <T> Map<String, T> unique(List<T> values, Function<T, String> id, String code) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            if (result.putIfAbsent(id.apply(value), value) != null) {
                throw new RuleEngineException(code, "rule input contains a duplicate identifier");
            }
        }
        return Map.copyOf(result);
    }
}
