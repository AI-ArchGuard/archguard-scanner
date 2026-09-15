package io.github.aiarchguard.scanner.parser.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Providers;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;
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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

final class JavaModelExtractor {

    private static final String LANGUAGE = "java";
    private static final Comparator<EdgeKey> EDGE_ORDER = Comparator.comparing(EdgeKey::sourceId)
            .thenComparing(key -> key.kind().wireValue())
            .thenComparing(EdgeKey::targetId);

    private static JavaParser newParser() {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setCharacterEncoding(StandardCharsets.UTF_8)
                .setAttributeComments(false)
                .setDetectOriginalLineSeparator(false)
                .setStoreTokens(true);
        return new JavaParser(configuration);
    }

    JavaScanResult extract(JavaScanRequest request, JavaSourceDiscovery.DiscoveryResult discovered) {
        List<JavaParseDiagnostic> diagnostics = new ArrayList<>(discovered.diagnostics());
        Project project = project(request);
        Map<String, Artifact> artifactsByModule = artifacts(request, project, discovered.files());
        List<ParsedSource> parsedSources = parseSources(request, discovered, diagnostics);
        List<TypeCandidate> candidates = typeCandidates(request, parsedSources, artifactsByModule);

        Map<String, List<TypeCandidate>> candidatesByName = new TreeMap<>();
        for (TypeCandidate candidate : candidates) {
            candidatesByName.computeIfAbsent(candidate.qualifiedName(), ignored -> new ArrayList<>()).add(candidate);
        }

        List<TypeInfo> types = new ArrayList<>();
        for (Map.Entry<String, List<TypeCandidate>> entry : candidatesByName.entrySet()) {
            if (entry.getValue().size() > 1) {
                entry.getValue().stream()
                        .sorted(Comparator.comparing(candidate -> candidate.source().file().repositoryPath()))
                        .forEach(candidate -> diagnostics.add(new JavaParseDiagnostic(
                                "java.type.duplicate",
                                candidate.source().file().repositoryPath(),
                                candidate.location().startLine(),
                                candidate.location().startColumn(),
                                "duplicate qualified type declaration was skipped")));
                continue;
            }
            TypeCandidate candidate = entry.getValue().getFirst();
            String componentId = StableIdGenerator.generate(
                    request.schemaVersion(),
                    request.projectIdentity(),
                    EntityKind.COMPONENT,
                    LANGUAGE,
                    candidate.source().file().repositoryPath(),
                    candidate.qualifiedName());
            Component component = new Component(
                    componentId,
                    candidate.artifact().id(),
                    ComponentKind.TYPE,
                    candidate.declaration().getNameAsString(),
                    LANGUAGE,
                    candidate.qualifiedName(),
                    candidate.location(),
                    componentExtensions(candidate.declaration()));
            types.add(new TypeInfo(candidate, component));
        }

        ResolutionIndex index = new ResolutionIndex(types);
        Map<String, Evidence> evidences = new LinkedHashMap<>();
        Map<EdgeKey, EdgeAccumulator> edges = new TreeMap<>(EDGE_ORDER);
        for (TypeInfo type : types.stream().sorted(Comparator.comparing(info -> info.component().id())).toList()) {
            extractDependencies(request, type, index, evidences, edges);
        }

        List<Dependency> dependencies = edges.values().stream()
                .map(edge -> edge.toDependency(request))
                .toList();
        return new JavaScanResult(
                project,
                List.copyOf(artifactsByModule.values()),
                types.stream().map(TypeInfo::component).toList(),
                dependencies,
                List.copyOf(evidences.values()),
                diagnostics);
    }

    private List<ParsedSource> parseSources(
            JavaScanRequest request,
            JavaSourceDiscovery.DiscoveryResult discovered,
        List<JavaParseDiagnostic> diagnostics) {
        List<ParsedSource> parsed = new ArrayList<>();
        JavaParser parser = newParser();
        long totalBytes = 0L;
        for (DiscoveredJavaFile file : discovered.files()) {
            try {
                if (Files.isSymbolicLink(file.absolutePath())) {
                    diagnostics.add(diagnostic(file, "java.path.symlink", 1, 1, "symbolic link was skipped"));
                    continue;
                }
                Path realPath = file.absolutePath().toRealPath();
                if (!realPath.startsWith(discovered.realRoot())) {
                    diagnostics.add(diagnostic(
                            file, "java.path.escape", 1, 1, "repository entry resolves outside the root"));
                    continue;
                }
                BasicFileAttributes attributes = Files.readAttributes(
                        file.absolutePath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile()) {
                    diagnostics.add(diagnostic(
                            file, "java.path.special-file", 1, 1, "non-regular Java source was skipped"));
                    continue;
                }
                long size = attributes.size();
                if (size > request.limits().maxFileBytes()) {
                    throw new JavaScanException(
                            "java.limit.file-bytes", "Java source exceeds the configured per-file limit");
                }
                if (totalBytes > request.limits().maxTotalBytes() - size) {
                    throw new JavaScanException(
                            "java.limit.total-bytes", "Java source bytes exceed the configured limit");
                }
                totalBytes += size;

                byte[] encodedSource = Files.readAllBytes(file.absolutePath());
                String sourceText;
                try {
                    sourceText = StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(encodedSource))
                            .toString();
                } finally {
                    Arrays.fill(encodedSource, (byte) 0);
                }
                ParseResult<CompilationUnit> result =
                        parser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(sourceText));
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    int[] position = firstProblemPosition(result);
                    diagnostics.add(diagnostic(
                            file,
                            "java.syntax.invalid",
                            position[0],
                            position[1],
                            "Java syntax is invalid"));
                    continue;
                }
                parsed.add(new ParsedSource(file, result.getResult().orElseThrow()));
            } catch (CharacterCodingException exception) {
                diagnostics.add(diagnostic(file, "java.encoding.invalid", 1, 1, "Java source is not valid UTF-8"));
            } catch (JavaScanException exception) {
                throw exception;
            } catch (IOException | RuntimeException exception) {
                diagnostics.add(diagnostic(file, "java.parse.failed", 1, 1, "Java source could not be parsed safely"));
            }
        }
        parsed.sort(Comparator.comparing(source -> source.file().repositoryPath()));
        return List.copyOf(parsed);
    }

    private static int[] firstProblemPosition(ParseResult<CompilationUnit> result) {
        if (result.getProblems().isEmpty()) {
            return new int[] {1, 1};
        }
        return result.getProblems().getFirst().getLocation()
                .flatMap(tokenRange -> tokenRange.getBegin().getRange())
                .map(range -> new int[] {range.begin.line, range.begin.column})
                .orElseGet(() -> new int[] {1, 1});
    }

    private static Project project(JavaScanRequest request) {
        String id = StableIdGenerator.generate(
                request.schemaVersion(),
                request.projectIdentity(),
                EntityKind.PROJECT,
                LANGUAGE,
                ".",
                request.projectIdentity());
        return new Project(
                id, request.projectIdentity(), request.projectName(), LANGUAGE, ".", Map.of());
    }

    private static Map<String, Artifact> artifacts(
            JavaScanRequest request, Project project, List<DiscoveredJavaFile> files) {
        Set<String> modules = new LinkedHashSet<>();
        files.stream().map(DiscoveredJavaFile::modulePath).sorted().forEach(modules::add);
        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        for (String module : modules) {
            String qualifiedName = request.projectIdentity() + ":" + module;
            String id = StableIdGenerator.generate(
                    request.schemaVersion(),
                    request.projectIdentity(),
                    EntityKind.ARTIFACT,
                    LANGUAGE,
                    module,
                    qualifiedName);
            String name = ".".equals(module)
                    ? request.projectName()
                    : module.substring(module.lastIndexOf('/') + 1);
            artifacts.put(
                    module,
                    new Artifact(
                            id,
                            project.id(),
                            ArtifactKind.MODULE,
                            name,
                            LANGUAGE,
                            module,
                            qualifiedName,
                            Map.of(
                                    "java.source-set", List.of("main"),
                                    "maven.module-path", List.of(module))));
        }
        return Collections.unmodifiableMap(artifacts);
    }

    private static List<TypeCandidate> typeCandidates(
            JavaScanRequest request, List<ParsedSource> sources, Map<String, Artifact> artifacts) {
        List<TypeCandidate> candidates = new ArrayList<>();
        for (ParsedSource source : sources) {
            Artifact artifact = artifacts.get(source.file().modulePath());
            for (TypeDeclaration<?> declaration : declarations(source.unit())) {
                String qualifiedName = qualifiedName(source.unit(), declaration);
                SourceLocation location = location(source.file().repositoryPath(), declaration);
                candidates.add(new TypeCandidate(source, declaration, qualifiedName, location, artifact));
            }
        }
        candidates.sort(Comparator.comparing(TypeCandidate::qualifiedName)
                .thenComparing(candidate -> candidate.source().file().repositoryPath()));
        return List.copyOf(candidates);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<TypeDeclaration<?>> declarations(CompilationUnit unit) {
        List<TypeDeclaration<?>> declarations = new ArrayList<>();
        for (Node node : unit.findAll(TypeDeclaration.class)) {
            TypeDeclaration<?> declaration = (TypeDeclaration<?>) node;
            if (isTopLevelOrMemberType(declaration)) {
                declarations.add(declaration);
            }
        }
        return List.copyOf(declarations);
    }

    private static boolean isTopLevelOrMemberType(TypeDeclaration<?> declaration) {
        Optional<Node> parent = declaration.getParentNode();
        return parent.isPresent()
                && (parent.get() instanceof CompilationUnit || parent.get() instanceof TypeDeclaration<?>);
    }

    private static String qualifiedName(CompilationUnit unit, TypeDeclaration<?> declaration) {
        List<String> names = new ArrayList<>();
        Node current = declaration;
        while (current instanceof TypeDeclaration<?> type) {
            names.add(type.getNameAsString());
            current = type.getParentNode().orElse(null);
        }
        Collections.reverse(names);
        String typeName = String.join(".", names);
        return unit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getNameAsString() + "." + typeName)
                .orElse(typeName);
    }

    private static SourceLocation location(String path, Node node) {
        Range range = node.getRange().orElseThrow(() -> new JavaScanException(
                "java.location.missing", "parsed Java declaration did not contain a source range"));
        return new SourceLocation(path, range.begin.line, range.begin.column, range.end.line, range.end.column);
    }

    private static Map<String, List<String>> componentExtensions(TypeDeclaration<?> declaration) {
        Map<String, List<String>> extensions = new TreeMap<>();
        extensions.put("java.declaration-kind", List.of(declarationKind(declaration)));
        List<String> modifiers = declaration.getModifiers().stream()
                .map(Modifier::getKeyword)
                .map(Modifier.Keyword::asString)
                .sorted()
                .toList();
        if (!modifiers.isEmpty()) {
            extensions.put("java.modifiers", modifiers);
        }
        extensions.put(
                "java.nesting",
                List.of(declaration.getParentNode().orElse(null) instanceof CompilationUnit ? "top-level" : "member"));
        return extensions;
    }

    private static String declarationKind(TypeDeclaration<?> declaration) {
        if (declaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
            return classOrInterface.isInterface() ? "interface" : "class";
        }
        if (declaration instanceof EnumDeclaration) {
            return "enum";
        }
        if (declaration instanceof RecordDeclaration) {
            return "record";
        }
        if (declaration instanceof AnnotationDeclaration) {
            return "annotation";
        }
        throw new JavaScanException("java.type.unsupported", "unsupported Java type declaration");
    }

    private static void extractDependencies(
            JavaScanRequest request,
            TypeInfo source,
            ResolutionIndex index,
            Map<String, Evidence> evidences,
            Map<EdgeKey, EdgeAccumulator> edges) {
        Set<Node> relationTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        TypeDeclaration<?> declaration = source.candidate().declaration();
        if (declaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
            classOrInterface.getExtendedTypes().forEach(type -> {
                markRelationType(relationTypes, type);
                addDependency(request, source, type, type.getNameWithScope(), DependencyKind.EXTENDS, index, evidences, edges);
            });
            classOrInterface.getImplementedTypes().forEach(type -> {
                markRelationType(relationTypes, type);
                addDependency(
                        request, source, type, type.getNameWithScope(), DependencyKind.IMPLEMENTS, index, evidences, edges);
            });
        } else if (declaration instanceof EnumDeclaration enumeration) {
            enumeration.getImplementedTypes().forEach(type -> {
                markRelationType(relationTypes, type);
                addDependency(
                        request, source, type, type.getNameWithScope(), DependencyKind.IMPLEMENTS, index, evidences, edges);
            });
        } else if (declaration instanceof RecordDeclaration record) {
            record.getImplementedTypes().forEach(type -> {
                markRelationType(relationTypes, type);
                addDependency(
                        request, source, type, type.getNameWithScope(), DependencyKind.IMPLEMENTS, index, evidences, edges);
            });
        }

        declaration.findAll(ClassOrInterfaceType.class).stream()
                .filter(type -> nearestType(type).map(declaration::equals).orElse(false))
                .filter(type -> !relationTypes.contains(type))
                .filter(type -> !isTypeParameter(type, declaration))
                .forEach(type -> addDependency(
                        request,
                        source,
                        type,
                        type.getNameWithScope(),
                        DependencyKind.DEPENDS_ON,
                        index,
                        evidences,
                        edges));

        declaration.findAll(AnnotationExpr.class).stream()
                .filter(annotation -> nearestType(annotation).map(declaration::equals).orElse(false))
                .forEach(annotation -> addDependency(
                        request,
                        source,
                        annotation,
                        annotation.getNameAsString(),
                        DependencyKind.DEPENDS_ON,
                        index,
                        evidences,
                        edges));
    }

    private static Optional<TypeDeclaration<?>> nearestType(Node node) {
        Node current = node.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?> type) {
                return Optional.of(type);
            }
            current = current.getParentNode().orElse(null);
        }
        return Optional.empty();
    }

    private static void markRelationType(Set<Node> relationTypes, ClassOrInterfaceType type) {
        ClassOrInterfaceType current = type;
        relationTypes.add(current);
        while (current.getScope().isPresent()) {
            current = current.getScope().orElseThrow();
            relationTypes.add(current);
        }
    }

    private static boolean isTypeParameter(ClassOrInterfaceType type, TypeDeclaration<?> owner) {
        if (type.getScope().isPresent()) {
            return false;
        }
        String name = type.getNameAsString();
        Node current = type.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof NodeWithTypeParameters<?> parameterOwner) {
                for (TypeParameter parameter : parameterOwner.getTypeParameters()) {
                    if (parameter.getNameAsString().equals(name)) {
                        return true;
                    }
                }
            }
            if (current == owner) {
                break;
            }
            current = current.getParentNode().orElse(null);
        }
        return false;
    }

    private static void addDependency(
            JavaScanRequest request,
            TypeInfo source,
            Node referenceNode,
            String referencedName,
            DependencyKind kind,
            ResolutionIndex index,
            Map<String, Evidence> evidences,
            Map<EdgeKey, EdgeAccumulator> edges) {
        Optional<TypeInfo> target = index.resolve(referencedName, source);
        if (target.isEmpty() || target.get().component().id().equals(source.component().id())) {
            return;
        }
        SourceLocation referenceLocation = location(source.candidate().source().file().repositoryPath(), referenceNode);
        String targetName = target.get().component().qualifiedName();
        String evidenceName = source.component().qualifiedName()
                + "|"
                + kind.wireValue()
                + "|"
                + targetName
                + "|"
                + referenceLocation.startLine()
                + ":"
                + referenceLocation.startColumn()
                + "-"
                + referenceLocation.endLine()
                + ":"
                + referenceLocation.endColumn();
        String evidenceId = StableIdGenerator.generate(
                request.schemaVersion(),
                request.projectIdentity(),
                EntityKind.EVIDENCE,
                LANGUAGE,
                referenceLocation.path(),
                evidenceName);
        Evidence evidence = new Evidence(
                evidenceId,
                kind == DependencyKind.EXTENDS || kind == DependencyKind.IMPLEMENTS
                        ? EvidenceKind.RELATION
                        : EvidenceKind.REFERENCE,
                source.component().qualifiedName() + " " + summaryVerb(kind) + " " + targetName,
                referenceLocation,
                Map.of());
        evidences.putIfAbsent(evidenceId, evidence);

        EdgeKey key = new EdgeKey(source.component().id(), kind, target.get().component().id());
        edges.computeIfAbsent(
                        key,
                        ignored -> new EdgeAccumulator(
                                source,
                                target.get(),
                                kind,
                                source.candidate().source().file().repositoryPath()))
                .evidenceIds()
                .add(evidenceId);
    }

    private static String summaryVerb(DependencyKind kind) {
        return switch (kind) {
            case EXTENDS -> "extends";
            case IMPLEMENTS -> "implements";
            default -> "references";
        };
    }

    private static JavaParseDiagnostic diagnostic(
            DiscoveredJavaFile file, String code, int line, int column, String message) {
        return new JavaParseDiagnostic(code, file.repositoryPath(), line, column, message);
    }

    private record ParsedSource(DiscoveredJavaFile file, CompilationUnit unit) {}

    private record TypeCandidate(
            ParsedSource source,
            TypeDeclaration<?> declaration,
            String qualifiedName,
            SourceLocation location,
            Artifact artifact) {}

    private record TypeInfo(TypeCandidate candidate, Component component) {}

    private record EdgeKey(String sourceId, DependencyKind kind, String targetId) {}

    private static final class EdgeAccumulator {
        private final TypeInfo source;
        private final TypeInfo target;
        private final DependencyKind kind;
        private final String path;
        private final Set<String> evidenceIds = new LinkedHashSet<>();

        private EdgeAccumulator(TypeInfo source, TypeInfo target, DependencyKind kind, String path) {
            this.source = source;
            this.target = target;
            this.kind = kind;
            this.path = path;
        }

        Set<String> evidenceIds() {
            return evidenceIds;
        }

        Dependency toDependency(JavaScanRequest request) {
            String qualifiedName = source.component().qualifiedName()
                    + "|"
                    + kind.wireValue()
                    + "|"
                    + target.component().qualifiedName();
            String id = StableIdGenerator.generate(
                    request.schemaVersion(),
                    request.projectIdentity(),
                    EntityKind.DEPENDENCY,
                    LANGUAGE,
                    path,
                    qualifiedName);
            return new Dependency(
                    id,
                    source.component().id(),
                    target.component().id(),
                    kind,
                    evidenceIds.stream().sorted().toList(),
                    Map.of());
        }
    }

    private static final class ResolutionIndex {
        private final Map<String, TypeInfo> byQualifiedName = new LinkedHashMap<>();
        private ResolutionIndex(List<TypeInfo> types) {
            types.stream().sorted(Comparator.comparing(type -> type.component().qualifiedName())).forEach(type -> {
                byQualifiedName.put(type.component().qualifiedName(), type);
            });
        }

        Optional<TypeInfo> resolve(String rawName, TypeInfo source) {
            TypeInfo direct = byQualifiedName.get(rawName);
            if (direct != null) {
                return Optional.of(direct);
            }

            String enclosing = source.component().qualifiedName();
            while (enclosing.contains(".")) {
                enclosing = enclosing.substring(0, enclosing.lastIndexOf('.'));
                TypeInfo nested = byQualifiedName.get(enclosing + "." + rawName);
                if (nested != null) {
                    return Optional.of(nested);
                }
            }

            String packageName = source.candidate().source().unit().getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString())
                    .orElse("");
            String samePackageName = packageName.isEmpty() ? rawName : packageName + "." + rawName;
            TypeInfo samePackage = byQualifiedName.get(samePackageName);
            if (samePackage != null) {
                return Optional.of(samePackage);
            }

            for (ImportDeclaration imported : source.candidate().source().unit().getImports()) {
                if (imported.isStatic()) {
                    continue;
                }
                String importedName = imported.getNameAsString();
                if (imported.isAsterisk()) {
                    TypeInfo wildcard = byQualifiedName.get(importedName + "." + rawName);
                    if (wildcard != null) {
                        return Optional.of(wildcard);
                    }
                    continue;
                }
                String simpleImportedName = importedName.substring(importedName.lastIndexOf('.') + 1);
                if (rawName.equals(simpleImportedName)) {
                    TypeInfo explicit = byQualifiedName.get(importedName);
                    if (explicit != null) {
                        return Optional.of(explicit);
                    }
                } else if (rawName.startsWith(simpleImportedName + ".")) {
                    TypeInfo importedNested =
                            byQualifiedName.get(importedName + rawName.substring(simpleImportedName.length()));
                    if (importedNested != null) {
                        return Optional.of(importedNested);
                    }
                }
            }

            return Optional.empty();
        }
    }
}
