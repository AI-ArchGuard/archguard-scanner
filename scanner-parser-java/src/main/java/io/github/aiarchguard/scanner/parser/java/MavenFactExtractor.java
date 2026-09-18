package io.github.aiarchguard.scanner.parser.java;

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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

final class MavenFactExtractor {

    private static final String LANGUAGE = "java";
    private static final int MAX_PROPERTY_PASSES = 16;
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    Facts extract(
            JavaScanRequest request,
            Project project,
            Map<String, Artifact> sourceArtifacts,
        List<JavaParseDiagnostic> diagnostics) {
        Map<String, RawPom> poms = new TreeMap<>();
        long totalPomBytes = 0L;
        for (String modulePath : sourceArtifacts.keySet()) {
            String pomPath = ".".equals(modulePath) ? "pom.xml" : modulePath + "/pom.xml";
            Path absolutePom = request.repositoryRoot().toAbsolutePath().normalize().resolve(pomPath).normalize();
            try {
                if (Files.isSymbolicLink(absolutePom)
                        || !Files.isRegularFile(absolutePom, LinkOption.NOFOLLOW_LINKS)
                        || !absolutePom.toRealPath().startsWith(request.repositoryRoot().toRealPath())) {
                    diagnostics.add(diagnostic("maven.pom.unsafe", pomPath, 1, "module POM is not a safe regular file"));
                    continue;
                }
                long pomBytes = Files.size(absolutePom);
                if (pomBytes > request.limits().maxFileBytes()) {
                    diagnostics.add(diagnostic("maven.pom.too-large", pomPath, 1, "module POM exceeds the file limit"));
                    continue;
                }
                if (totalPomBytes > request.limits().maxTotalBytes() - pomBytes) {
                    throw new JavaScanException(
                            "maven.limit.total-bytes", "module POM bytes exceed the configured limit");
                }
                totalPomBytes += pomBytes;
                poms.put(modulePath, parse(absolutePom, pomPath));
            } catch (IOException | XMLStreamException exception) {
                diagnostics.add(diagnostic("maven.pom.invalid", pomPath, 1, "module POM could not be parsed safely"));
            }
        }

        Map<String, RawPom> localParents = new TreeMap<>();
        Map<Path, RawPom> parentCache = new LinkedHashMap<>();
        Path repositoryRoot = request.repositoryRoot().toAbsolutePath().normalize();
        for (Map.Entry<String, RawPom> entry : poms.entrySet()) {
            RawPom pom = entry.getValue();
            if (pom.parentArtifactId() == null) {
                continue;
            }
            String relativeParent = pom.parentRelativePath() == null ? "../pom.xml" : pom.parentRelativePath();
            if (relativeParent.isBlank()) {
                continue;
            }
            Path modulePom = repositoryRoot.resolve(pom.repositoryPath()).normalize();
            Path parentPom = modulePom.getParent().resolve(relativeParent).normalize();
            if (!parentPom.startsWith(repositoryRoot)) {
                diagnostics.add(diagnostic(
                        "maven.parent.outside-root",
                        pom.repositoryPath(),
                        1,
                        "local parent POM path leaves the repository root"));
                continue;
            }
            try {
                if (Files.isRegularFile(parentPom, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(parentPom)
                        && parentPom.toRealPath().startsWith(request.repositoryRoot().toRealPath())) {
                    RawPom parsedParent = parentCache.get(parentPom);
                    if (parsedParent == null) {
                        long parentBytes = Files.size(parentPom);
                        if (parentBytes > request.limits().maxFileBytes()) {
                            diagnostics.add(diagnostic(
                                    "maven.parent.too-large", pom.repositoryPath(), 1, "local parent POM exceeds the file limit"));
                            continue;
                        }
                        if (totalPomBytes > request.limits().maxTotalBytes() - parentBytes) {
                            throw new JavaScanException(
                                    "maven.limit.total-bytes", "module POM bytes exceed the configured limit");
                        }
                        totalPomBytes += parentBytes;
                        String parentRepositoryPath = repositoryRoot.relativize(parentPom).toString().replace('\\', '/');
                        parsedParent = parse(parentPom, parentRepositoryPath);
                        parentCache.put(parentPom, parsedParent);
                    }
                    localParents.put(entry.getKey(), parsedParent);
                }
            } catch (IOException | XMLStreamException exception) {
                diagnostics.add(diagnostic(
                        "maven.parent.invalid", pom.repositoryPath(), 1, "local parent POM could not be parsed safely"));
            }
        }

        Map<String, Coordinate> moduleCoordinates = new TreeMap<>();
        for (Map.Entry<String, RawPom> entry : poms.entrySet()) {
            RawPom pom = entry.getValue();
            Map<String, String> properties = projectProperties(pom, localParents.get(entry.getKey()));
            String group = resolve(firstNonBlank(pom.groupId(), pom.parentGroupId()), properties);
            String artifact = resolve(pom.artifactId(), properties);
            String version = resolve(firstNonBlank(pom.version(), pom.parentVersion()), properties);
            if (group == null || artifact == null) {
                continue;
            }
            moduleCoordinates.put(entry.getKey(), new Coordinate(group, artifact, version));
        }

        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        for (Map.Entry<String, Artifact> entry : sourceArtifacts.entrySet()) {
            Coordinate coordinate = moduleCoordinates.get(entry.getKey());
            Artifact original = entry.getValue();
            Map<String, List<String>> extensions = new TreeMap<>(original.extensions());
            if (coordinate == null) {
                extensions.put("maven.coordinate-unresolved", List.of("true"));
            } else {
                extensions.put("maven.coordinate", List.of(coordinate.value()));
                extensions.put("maven.group-id", List.of(coordinate.groupId()));
                extensions.put("maven.artifact-id", List.of(coordinate.artifactId()));
                if (coordinate.version() != null) {
                    extensions.put("maven.version", List.of(coordinate.version()));
                } else {
                    extensions.put("maven.version-unresolved", List.of("true"));
                }
            }
            artifacts.put(entry.getKey(), new Artifact(
                    original.id(),
                    original.projectId(),
                    original.kind(),
                    original.name(),
                    original.language(),
                    original.repositoryPath(),
                    original.qualifiedName(),
                    extensions));
        }

        Map<String, Artifact> artifactsByGa = new LinkedHashMap<>();
        moduleCoordinates.forEach((path, coordinate) -> artifactsByGa.put(coordinate.ga(), artifacts.get(path)));
        Map<String, Component> moduleComponents = new LinkedHashMap<>();

        Map<String, Artifact> externalArtifacts = new TreeMap<>();
        Map<String, Component> externalComponents = new TreeMap<>();
        Map<String, Evidence> evidences = new TreeMap<>();
        Map<String, DependencyAccumulator> dependencyAccumulators = new TreeMap<>();
        for (Map.Entry<String, RawPom> entry : poms.entrySet()) {
            Artifact sourceArtifact = artifacts.get(entry.getKey());
            RawPom pom = entry.getValue();
            Map<String, String> properties = projectProperties(pom, localParents.get(entry.getKey()));
            Coordinate projectCoordinate = moduleCoordinates.get(entry.getKey());
            if (projectCoordinate != null) {
                properties.put("project.groupId", projectCoordinate.groupId());
                properties.put("pom.groupId", projectCoordinate.groupId());
                properties.put("project.artifactId", projectCoordinate.artifactId());
                properties.put("pom.artifactId", projectCoordinate.artifactId());
                if (projectCoordinate.version() != null) {
                    properties.put("project.version", projectCoordinate.version());
                    properties.put("pom.version", projectCoordinate.version());
                }
            }
            for (RawDependency rawDependency : pom.dependencies()) {
                String scope = resolve(rawDependency.scope(), properties);
                if (scope == null || scope.isBlank()) {
                    scope = "compile";
                }
                if ("test".equals(scope)) {
                    continue;
                }
                Coordinate sourceCoordinate = moduleCoordinates.get(entry.getKey());
                String sourceName = sourceCoordinate == null ? sourceArtifact.qualifiedName() : sourceCoordinate.value();
                Component sourceComponent = moduleComponents.computeIfAbsent(
                        sourceArtifact.id(),
                        ignored -> moduleComponent(request, sourceArtifact, sourceName, pomPath(entry.getKey())));
                String group = resolve(rawDependency.groupId(), properties);
                String artifactName = resolve(rawDependency.artifactId(), properties);
                String version = resolve(rawDependency.version(), properties);
                if (group == null || artifactName == null) {
                    diagnostics.add(diagnostic(
                            "maven.dependency.unresolved",
                            pom.repositoryPath(),
                            rawDependency.line(),
                            "direct dependency Maven G:A could not be resolved"));
                    continue;
                }
                Coordinate coordinate = new Coordinate(group, artifactName, version);
                Artifact targetArtifact = artifactsByGa.get(coordinate.ga());
                Component targetComponent;
                if (targetArtifact != null) {
                    Artifact resolvedTargetArtifact = targetArtifact;
                    targetComponent = moduleComponents.computeIfAbsent(
                            targetArtifact.id(),
                            ignored -> moduleComponent(
                                    request,
                                    resolvedTargetArtifact,
                                    coordinate.value(),
                                    pomPath(resolvedTargetArtifact.repositoryPath())));
                } else {
                    targetArtifact = externalArtifacts.computeIfAbsent(coordinate.value(), ignored -> externalArtifact(
                            request, project, coordinate));
                    Artifact resolvedTargetArtifact = targetArtifact;
                    targetComponent = externalComponents.computeIfAbsent(
                            coordinate.value(), ignored -> moduleComponent(request, resolvedTargetArtifact, coordinate.value(), "."));
                }
                SourceLocation location = new SourceLocation(
                        pom.repositoryPath(), rawDependency.line(), 1, rawDependency.line(), 1);
                String evidenceName = sourceComponent.qualifiedName() + "|maven|" + coordinate.value() + "|" + rawDependency.line();
                String evidenceId = StableIdGenerator.generate(
                        request.schemaVersion(),
                        request.projectIdentity(),
                        EntityKind.EVIDENCE,
                        LANGUAGE,
                        pom.repositoryPath(),
                        evidenceName);
                evidences.putIfAbsent(evidenceId, new Evidence(
                        evidenceId,
                        EvidenceKind.CONFIGURATION,
                        "Maven module declares direct " + scope + " dependency " + coordinate.value(),
                        location,
                        Map.of("maven.scope", List.of(scope))));
                String dependencyKey = sourceComponent.id() + "|" + targetComponent.id();
                dependencyAccumulators
                        .computeIfAbsent(
                                dependencyKey,
                                ignored -> new DependencyAccumulator(sourceComponent, targetComponent, pom.repositoryPath()))
                        .add(evidenceId, scope);
            }
        }
        artifacts.putAll(externalArtifacts);
        List<Component> components = new ArrayList<>(moduleComponents.values());
        components.addAll(externalComponents.values());
        List<Dependency> dependencies = dependencyAccumulators.values().stream()
                .map(value -> value.toDependency(request))
                .toList();
        return new Facts(
                List.copyOf(artifacts.values()),
                components,
                dependencies,
                List.copyOf(evidences.values()));
    }

    private static RawPom parse(Path pom, String repositoryPath) throws IOException, XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        try (InputStream input = Files.newInputStream(pom)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            Deque<String> path = new ArrayDeque<>();
            String groupId = null;
            String artifactId = null;
            String version = null;
            String parentGroupId = null;
            String parentArtifactId = null;
            String parentVersion = null;
            String parentRelativePath = null;
            boolean parentRelativePathDeclared = false;
            Map<String, String> properties = new TreeMap<>();
            List<RawDependency> dependencies = new ArrayList<>();
            RawDependencyBuilder dependency = null;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw new XMLStreamException("DTD and entities are forbidden");
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    path.addLast(reader.getLocalName());
                    if ("project/parent/relativePath".equals(String.join("/", path))) {
                        parentRelativePathDeclared = true;
                    }
                    if (isDirectDependency(path)) {
                        dependency = new RawDependencyBuilder(reader.getLocation().getLineNumber());
                    }
                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    if (reader.isWhiteSpace()) {
                        continue;
                    }
                    String value = reader.getText().trim();
                    String joined = String.join("/", path);
                    if ("project/groupId".equals(joined)) groupId = append(groupId, value);
                    else if ("project/artifactId".equals(joined)) artifactId = append(artifactId, value);
                    else if ("project/version".equals(joined)) version = append(version, value);
                    else if ("project/parent/groupId".equals(joined)) parentGroupId = append(parentGroupId, value);
                    else if ("project/parent/artifactId".equals(joined)) parentArtifactId = append(parentArtifactId, value);
                    else if ("project/parent/version".equals(joined)) parentVersion = append(parentVersion, value);
                    else if ("project/parent/relativePath".equals(joined)) parentRelativePath = append(parentRelativePath, value);
                    else if (path.size() == 3 && "project".equals(path.getFirst()) && "properties".equals(element(path, 1))) {
                        properties.merge(path.getLast(), value, String::concat);
                    } else if (dependency != null && path.size() == 4) {
                        dependency.accept(path.getLast(), value);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (dependency != null && isDirectDependency(path)) {
                        dependencies.add(dependency.build());
                        dependency = null;
                    }
                    path.removeLast();
                }
            }
            reader.close();
            return new RawPom(
                    repositoryPath,
                    trim(groupId),
                    trim(artifactId),
                    trim(version),
                    trim(parentGroupId),
                    trim(parentArtifactId),
                    trim(parentVersion),
                    parentRelativePathDeclared ? (parentRelativePath == null ? "" : parentRelativePath.trim()) : null,
                    Map.copyOf(properties),
                    List.copyOf(dependencies));
        }
    }

    private static boolean isDirectDependency(Deque<String> path) {
        return path.size() == 3
                && "project".equals(path.getFirst())
                && "dependencies".equals(element(path, 1))
                && "dependency".equals(path.getLast());
    }

    private static String element(Deque<String> path, int index) {
        return path.stream().skip(index).findFirst().orElse("");
    }

    private static Map<String, String> projectProperties(RawPom pom, RawPom localParent) {
        Map<String, String> result = new TreeMap<>();
        if (localParent != null) {
            result.putAll(localParent.properties());
        }
        result.putAll(pom.properties());
        if (pom.parentGroupId() != null) result.put("parent.groupId", pom.parentGroupId());
        if (pom.parentArtifactId() != null) result.put("parent.artifactId", pom.parentArtifactId());
        if (pom.parentVersion() != null) result.put("parent.version", pom.parentVersion());
        return result;
    }

    private static String resolve(String value, Map<String, String> properties) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String resolved = value.trim();
        Set<String> seen = new TreeSet<>();
        for (int pass = 0; pass < MAX_PROPERTY_PASSES; pass++) {
            Matcher matcher = PROPERTY.matcher(resolved);
            if (!matcher.find()) {
                return resolved.isBlank() ? null : resolved;
            }
            if (!seen.add(resolved)) {
                return null;
            }
            StringBuffer output = new StringBuffer();
            do {
                String replacement = properties.get(matcher.group(1));
                if (replacement == null) {
                    return null;
                }
                matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
            } while (matcher.find());
            matcher.appendTail(output);
            resolved = output.toString();
        }
        return null;
    }

    private static Artifact externalArtifact(JavaScanRequest request, Project project, Coordinate coordinate) {
        String id = StableIdGenerator.generate(
                request.schemaVersion(),
                request.projectIdentity(),
                EntityKind.ARTIFACT,
                LANGUAGE,
                ".",
                coordinate.value());
        Map<String, List<String>> extensions = new TreeMap<>();
        extensions.put("maven.coordinate", List.of(coordinate.value()));
        extensions.put("maven.external", List.of("true"));
        extensions.put("maven.group-id", List.of(coordinate.groupId()));
        extensions.put("maven.artifact-id", List.of(coordinate.artifactId()));
        if (coordinate.version() != null) {
            extensions.put("maven.version", List.of(coordinate.version()));
        } else {
            extensions.put("maven.version-unresolved", List.of("true"));
        }
        return new Artifact(
                id,
                project.id(),
                ArtifactKind.LIBRARY,
                coordinate.artifactId(),
                LANGUAGE,
                ".",
                coordinate.value(),
                extensions);
    }

    private static Component moduleComponent(
            JavaScanRequest request, Artifact artifact, String qualifiedName, String locationPath) {
        String id = StableIdGenerator.generate(
                request.schemaVersion(),
                request.projectIdentity(),
                EntityKind.COMPONENT,
                LANGUAGE,
                locationPath,
                qualifiedName);
        SourceLocation location = ".".equals(locationPath) ? null : new SourceLocation(locationPath, 1, 1, 1, 1);
        return new Component(
                id,
                artifact.id(),
                ComponentKind.MODULE,
                artifact.name(),
                LANGUAGE,
                qualifiedName,
                location,
                Map.of("maven.module-component", List.of("true")));
    }

    private static String pomPath(String modulePath) {
        return ".".equals(modulePath) ? "pom.xml" : modulePath + "/pom.xml";
    }

    private static String append(String existing, String value) {
        return existing == null ? value : existing + value;
    }

    private static String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static JavaParseDiagnostic diagnostic(String code, String path, int line, String message) {
        return new JavaParseDiagnostic(code, path, Math.max(1, line), 1, message);
    }

    record Facts(
            List<Artifact> artifacts,
            List<Component> components,
            List<Dependency> dependencies,
            List<Evidence> evidences) {}

    private record RawPom(
            String repositoryPath,
            String groupId,
            String artifactId,
            String version,
            String parentGroupId,
            String parentArtifactId,
            String parentVersion,
            String parentRelativePath,
            Map<String, String> properties,
            List<RawDependency> dependencies) {}

    private record RawDependency(String groupId, String artifactId, String version, String scope, int line) {}

    private static final class RawDependencyBuilder {
        private String groupId;
        private String artifactId;
        private String version;
        private String scope;
        private final int line;

        private RawDependencyBuilder(int line) {
            this.line = Math.max(1, line);
        }

        void accept(String name, String value) {
            switch (name) {
                case "groupId" -> groupId = append(groupId, value);
                case "artifactId" -> artifactId = append(artifactId, value);
                case "version" -> version = append(version, value);
                case "scope" -> scope = append(scope, value);
                default -> { }
            }
        }

        RawDependency build() {
            return new RawDependency(trim(groupId), trim(artifactId), trim(version), trim(scope), line);
        }
    }

    private record Coordinate(String groupId, String artifactId, String version) {
        String ga() {
            return groupId + ":" + artifactId;
        }

        String value() {
            return version == null ? ga() : ga() + ":" + version;
        }
    }

    private static final class DependencyAccumulator {
        private final Component source;
        private final Component target;
        private final String path;
        private final Set<String> evidenceIds = new TreeSet<>();
        private final Set<String> scopes = new TreeSet<>();

        private DependencyAccumulator(Component source, Component target, String path) {
            this.source = source;
            this.target = target;
            this.path = path;
        }

        void add(String evidenceId, String scope) {
            evidenceIds.add(evidenceId);
            scopes.add(scope);
        }

        Dependency toDependency(JavaScanRequest request) {
            String name = source.qualifiedName() + "|depends-on|" + target.qualifiedName();
            String id = StableIdGenerator.generate(
                    request.schemaVersion(),
                    request.projectIdentity(),
                    EntityKind.DEPENDENCY,
                    LANGUAGE,
                    path,
                    name);
            return new Dependency(
                    id,
                    source.id(),
                    target.id(),
                    DependencyKind.DEPENDS_ON,
                    List.copyOf(evidenceIds),
                    Map.of("maven.scope", List.copyOf(scopes)));
        }
    }
}
