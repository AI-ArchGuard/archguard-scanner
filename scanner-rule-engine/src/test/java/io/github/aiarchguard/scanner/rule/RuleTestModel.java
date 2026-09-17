package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.ArtifactKind;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.ComponentKind;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.DependencyKind;
import io.github.aiarchguard.scanner.domain.model.EntityKind;
import io.github.aiarchguard.scanner.domain.model.Evidence;
import io.github.aiarchguard.scanner.domain.model.EvidenceKind;
import io.github.aiarchguard.scanner.domain.model.Project;
import io.github.aiarchguard.scanner.domain.model.SourceLocation;
import io.github.aiarchguard.scanner.domain.model.StableIdGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RuleTestModel {

    private static final String VERSION = "0.1.0";
    private static final String IDENTITY = "example/structure-rules";
    private static final String LANGUAGE = "java";

    private RuleTestModel() {}

    static Model model(List<Node> nodes, List<Link> links) {
        String projectId = id(EntityKind.PROJECT, ".", IDENTITY);
        Project project = new Project(projectId, IDENTITY, "structure-rules", LANGUAGE, ".", Map.of());

        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        for (Node node : nodes) {
            artifacts.computeIfAbsent(node.module(), module -> new Artifact(
                    id(EntityKind.ARTIFACT, module, module),
                    projectId,
                    ArtifactKind.MODULE,
                    module,
                    LANGUAGE,
                    module,
                    module,
                    Map.of()));
        }

        Map<String, Component> components = new LinkedHashMap<>();
        for (Node node : nodes) {
            String qualifiedName = "<default>".equals(node.packageName())
                    ? node.name()
                    : node.packageName() + "." + node.name();
            String path = node.module() + "/src/" + node.name() + ".java";
            components.put(node.name(), new Component(
                    id(EntityKind.COMPONENT, path, qualifiedName),
                    artifacts.get(node.module()).id(),
                    ComponentKind.TYPE,
                    node.name(),
                    LANGUAGE,
                    qualifiedName,
                    new SourceLocation(path, 1, 1, 1, 10),
                    Map.of("java.package", List.of(node.packageName()))));
        }

        Map<String, Evidence> evidences = new LinkedHashMap<>();
        Map<String, Dependency> dependencies = new LinkedHashMap<>();
        for (Link link : links) {
            Component source = required(components, link.source());
            Component target = required(components, link.target());
            String evidenceId = id(EntityKind.EVIDENCE, source.location().path(), link.name());
            evidences.put(link.name(), new Evidence(
                    evidenceId,
                    EvidenceKind.REFERENCE,
                    "reference " + link.name(),
                    source.location(),
                    Map.of()));
            dependencies.put(link.name(), new Dependency(
                    id(EntityKind.DEPENDENCY, source.location().path(), link.name()),
                    source.id(),
                    target.id(),
                    DependencyKind.DEPENDS_ON,
                    List.of(evidenceId),
                    Map.of()));
        }
        return new Model(
                new RuleInput(
                        VERSION,
                        project,
                        List.copyOf(artifacts.values()),
                        List.copyOf(components.values()),
                        List.copyOf(dependencies.values()),
                        List.copyOf(evidences.values())),
                Map.copyOf(components),
                Map.copyOf(dependencies));
    }

    private static String id(EntityKind kind, String path, String qualifiedName) {
        return StableIdGenerator.generate(VERSION, IDENTITY, kind, LANGUAGE, path, qualifiedName);
    }

    private static <T> T required(Map<String, T> values, String key) {
        T value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("unknown test node " + key);
        }
        return value;
    }

    record Node(String name, String packageName, String module) {}

    record Link(String name, String source, String target) {}

    record Model(RuleInput input, Map<String, Component> components, Map<String, Dependency> dependencies) {
        Model reversed() {
            List<Artifact> artifacts = new ArrayList<>(input.artifacts());
            List<Component> componentList = new ArrayList<>(input.components());
            List<Dependency> dependencyList = new ArrayList<>(input.dependencies());
            List<Evidence> evidences = new ArrayList<>(input.evidences());
            java.util.Collections.reverse(artifacts);
            java.util.Collections.reverse(componentList);
            java.util.Collections.reverse(dependencyList);
            java.util.Collections.reverse(evidences);
            return new Model(
                    new RuleInput(
                            input.schemaVersion(),
                            input.project(),
                            artifacts,
                            componentList,
                            dependencyList,
                            evidences),
                    components,
                    dependencies);
        }
    }
}
