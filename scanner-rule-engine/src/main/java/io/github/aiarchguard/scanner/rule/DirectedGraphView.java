package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Dependency;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class DirectedGraphView {

    private final List<String> nodes;
    private final List<Edge> edges;
    private final Map<String, List<String>> outgoing;
    private final Map<String, List<String>> incoming;

    DirectedGraphView(Collection<String> nodes, Collection<Edge> edges) {
        this.nodes = nodes.stream().distinct().sorted().toList();
        Set<String> nodeSet = Set.copyOf(this.nodes);
        this.edges = edges.stream()
                .sorted(Comparator.comparing((Edge edge) -> edge.dependency().id())
                        .thenComparing(Edge::source)
                        .thenComparing(Edge::target))
                .toList();
        if (this.edges.stream().anyMatch(edge -> !nodeSet.contains(edge.source()) || !nodeSet.contains(edge.target()))) {
            throw new RuleEngineException("rule.input.dangling-dependency", "graph edge references an unknown node");
        }
        this.outgoing = adjacency(this.nodes, this.edges, true);
        this.incoming = adjacency(this.nodes, this.edges, false);
    }

    List<String> nodes() {
        return nodes;
    }

    List<Edge> edges() {
        return edges;
    }

    List<List<String>> stronglyConnectedComponents() {
        Set<String> visited = new HashSet<>();
        List<String> finishOrder = new ArrayList<>(nodes.size());
        for (String start : nodes) {
            if (!visited.add(start)) {
                continue;
            }
            Deque<Frame> stack = new ArrayDeque<>();
            stack.push(new Frame(start));
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                List<String> targets = outgoing.get(frame.node);
                if (frame.nextIndex < targets.size()) {
                    String target = targets.get(frame.nextIndex++);
                    if (visited.add(target)) {
                        stack.push(new Frame(target));
                    }
                } else {
                    finishOrder.add(frame.node);
                    stack.pop();
                }
            }
        }

        visited.clear();
        List<List<String>> components = new ArrayList<>();
        for (int index = finishOrder.size() - 1; index >= 0; index--) {
            String start = finishOrder.get(index);
            if (!visited.add(start)) {
                continue;
            }
            TreeSet<String> component = new TreeSet<>();
            Deque<String> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                String node = stack.pop();
                component.add(node);
                List<String> sources = incoming.get(node);
                for (int sourceIndex = sources.size() - 1; sourceIndex >= 0; sourceIndex--) {
                    String source = sources.get(sourceIndex);
                    if (visited.add(source)) {
                        stack.push(source);
                    }
                }
            }
            components.add(List.copyOf(component));
        }
        components.sort(Comparator.comparing(component -> component.getFirst()));
        return List.copyOf(components);
    }

    List<List<String>> cyclicStronglyConnectedComponents() {
        return stronglyConnectedComponents().stream()
                .filter(component -> component.size() > 1 || outgoing.get(component.getFirst()).contains(component.getFirst()))
                .toList();
    }

    List<Edge> edgesWithin(Collection<String> component) {
        Set<String> members = Set.copyOf(component);
        return edges.stream()
                .filter(edge -> members.contains(edge.source()) && members.contains(edge.target()))
                .toList();
    }

    private static Map<String, List<String>> adjacency(List<String> nodes, List<Edge> edges, boolean forward) {
        Map<String, TreeSet<String>> working = new LinkedHashMap<>();
        nodes.forEach(node -> working.put(node, new TreeSet<>()));
        for (Edge edge : edges) {
            String from = forward ? edge.source() : edge.target();
            String to = forward ? edge.target() : edge.source();
            working.get(from).add(to);
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        working.forEach((node, targets) -> result.put(node, List.copyOf(targets)));
        return Map.copyOf(result);
    }

    record Edge(String source, String target, Dependency dependency) {
        Edge {
            if (source == null || target == null || dependency == null) {
                throw new IllegalArgumentException("graph edge values must not be null");
            }
        }
    }

    private static final class Frame {
        private final String node;
        private int nextIndex;

        private Frame(String node) {
            this.node = node;
        }
    }
}
