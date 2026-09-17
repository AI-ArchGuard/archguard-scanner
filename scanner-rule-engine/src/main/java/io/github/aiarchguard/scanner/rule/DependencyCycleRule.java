package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class DependencyCycleRule {

    List<Finding> evaluate(RuleInput input, DependencyCycleConfig config, int maxFindings) {
        Map<String, Component> components = input.componentIndex();
        Map<String, Artifact> artifacts = input.artifactIndex();
        Function<Component, String> classifier = switch (config.scope()) {
            case COMPONENT -> Component::id;
            case PACKAGE -> ComponentFacts::packageName;
            case MODULE -> Component::artifactId;
        };
        Map<String, String> labels = new LinkedHashMap<>();
        for (Component component : input.components()) {
            String node = classifier.apply(component);
            String label = switch (config.scope()) {
                case COMPONENT -> component.qualifiedName();
                case PACKAGE -> ComponentFacts.packageName(component);
                case MODULE -> artifacts.get(component.artifactId()).qualifiedName();
            };
            labels.putIfAbsent(node, label);
        }

        List<DirectedGraphView.Edge> edges = new ArrayList<>();
        for (Dependency dependency : input.dependencies()) {
            String source = classifier.apply(components.get(dependency.sourceId()));
            String target = classifier.apply(components.get(dependency.targetId()));
            if (config.scope() != CycleScope.COMPONENT && source.equals(target)) {
                continue;
            }
            edges.add(new DirectedGraphView.Edge(source, target, dependency));
        }
        DirectedGraphView graph = new DirectedGraphView(labels.keySet(), edges);
        List<Finding> findings = new ArrayList<>();
        for (List<String> component : graph.cyclicStronglyConnectedComponents()) {
            if (findings.size() >= maxFindings) {
                throw new RuleEngineException("rule.limit.findings", "rule finding limit exceeded");
            }
            List<Dependency> internalDependencies = graph.edgesWithin(component).stream()
                    .map(DirectedGraphView.Edge::dependency)
                    .toList();
            List<String> componentLabels = component.stream().map(labels::get).sorted().toList();
            findings.add(FindingFactory.cycle(
                    input,
                    config.rule(),
                    config.severity(),
                    config.scope(),
                    component,
                    internalDependencies,
                    "Dependency cycle detected in " + config.scope().wireValue() + " scope: "
                            + String.join(", ", componentLabels)));
        }
        return findings.stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }
}
