package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DependencyGraph {

    private final Map<String, Component> components;
    private final List<Dependency> dependencies;
    private final Map<String, List<Dependency>> outgoing;
    private final Map<String, List<Dependency>> incoming;
    private final DirectedGraphView view;

    private DependencyGraph(List<Component> components, List<Dependency> dependencies, RuleExecutionLimits limits) {
        if (components.size() > limits.maxNodes()) {
            throw new RuleEngineException("rule.limit.nodes", "dependency graph node limit exceeded");
        }
        if (dependencies.size() > limits.maxEdges()) {
            throw new RuleEngineException("rule.limit.edges", "dependency graph edge limit exceeded");
        }
        Map<String, Component> componentIndex = new LinkedHashMap<>();
        for (Component component : components) {
            if (componentIndex.putIfAbsent(component.id(), component) != null) {
                throw new RuleEngineException(
                        "rule.input.duplicate-component", "dependency graph contains a duplicate component");
            }
        }
        Map<String, Dependency> dependencyIndex = new LinkedHashMap<>();
        for (Dependency dependency : dependencies) {
            if (dependencyIndex.putIfAbsent(dependency.id(), dependency) != null) {
                throw new RuleEngineException(
                        "rule.input.duplicate-dependency", "dependency graph contains a duplicate dependency");
            }
            if (!componentIndex.containsKey(dependency.sourceId())
                    || !componentIndex.containsKey(dependency.targetId())) {
                throw new RuleEngineException(
                        "rule.input.dangling-dependency", "dependency graph edge references an unknown component");
            }
        }
        this.components = Map.copyOf(componentIndex);
        this.dependencies = dependencyIndex.values().stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
        this.outgoing = index(this.components, this.dependencies, true);
        this.incoming = index(this.components, this.dependencies, false);
        this.view = new DirectedGraphView(
                this.components.keySet(),
                this.dependencies.stream()
                        .map(dependency -> new DirectedGraphView.Edge(
                                dependency.sourceId(), dependency.targetId(), dependency))
                        .toList());
    }

    public static DependencyGraph create(
            List<Component> components, List<Dependency> dependencies, RuleExecutionLimits limits) {
        if (components == null || dependencies == null || limits == null) {
            throw new IllegalArgumentException("graph input and limits must not be null");
        }
        if (components.stream().anyMatch(value -> value == null)
                || dependencies.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("graph input must not contain null");
        }
        return new DependencyGraph(components, dependencies, limits);
    }

    public List<Component> components() {
        return components.values().stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }

    public List<Dependency> dependencies() {
        return dependencies;
    }

    public List<Dependency> outgoing(String componentId) {
        return requireNode(outgoing, componentId);
    }

    public List<Dependency> incoming(String componentId) {
        return requireNode(incoming, componentId);
    }

    public List<List<String>> stronglyConnectedComponents() {
        return view.stronglyConnectedComponents();
    }

    public List<List<String>> cyclicStronglyConnectedComponents() {
        return view.cyclicStronglyConnectedComponents();
    }

    private static Map<String, List<Dependency>> index(
            Map<String, Component> components, List<Dependency> dependencies, boolean forward) {
        Map<String, List<Dependency>> result = new LinkedHashMap<>();
        for (String id : components.keySet().stream().sorted().toList()) {
            result.put(
                    id,
                    dependencies.stream()
                            .filter(dependency -> (forward ? dependency.sourceId() : dependency.targetId()).equals(id))
                            .toList());
        }
        return Map.copyOf(result);
    }

    private static List<Dependency> requireNode(Map<String, List<Dependency>> index, String componentId) {
        List<Dependency> values = index.get(componentId);
        if (values == null) {
            throw new IllegalArgumentException("unknown component id");
        }
        return values;
    }
}
