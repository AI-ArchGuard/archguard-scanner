package io.github.aiarchguard.scanner.parser.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.DependencyKind;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceScannerTest {

    private final JavaSourceScanner scanner = new JavaSourceScanner();

    @Test
    void extractsTypesRelationsReferencesAndSafeEvidence() {
        JavaScanResult result = scanner.scan(request("single-module"));

        assertEquals(Set.of("."), result.artifacts().stream()
                .map(artifact -> artifact.repositoryPath())
                .collect(Collectors.toSet()));
        Map<String, Component> components = result.components().stream()
                .collect(Collectors.toMap(Component::qualifiedName, Function.identity()));
        assertEquals(
                Set.of(
                        "com.example.Audit",
                        "com.example.Marker",
                        "com.example.Order",
                        "com.example.Port",
                        "com.example.Repository",
                        "com.example.Service",
                        "com.example.Service.Nested",
                        "com.example.Status"),
                components.keySet());
        assertEquals(
                Set.of("class"), components.get("com.example.Service").extensions().get("java.declaration-kind").stream().collect(Collectors.toSet()));
        assertEquals(
                Set.of("member"), components.get("com.example.Service.Nested").extensions().get("java.nesting").stream().collect(Collectors.toSet()));

        assertTrue(hasDependency(result, "com.example.Service", "com.example.Port", DependencyKind.IMPLEMENTS));
        assertTrue(hasDependency(result, "com.example.Service", "com.example.Audit", DependencyKind.DEPENDS_ON));
        assertTrue(hasDependency(result, "com.example.Service", "com.example.Repository", DependencyKind.DEPENDS_ON));
        assertTrue(hasDependency(result, "com.example.Service", "com.example.Marker", DependencyKind.DEPENDS_ON));
        assertTrue(hasDependency(result, "com.example.Service.Nested", "com.example.Repository", DependencyKind.EXTENDS));
        assertTrue(hasDependency(result, "com.example.Order", "com.example.Port", DependencyKind.IMPLEMENTS));
        assertTrue(hasDependency(result, "com.example.Status", "com.example.Port", DependencyKind.IMPLEMENTS));
        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.evidences().stream().allMatch(evidence -> !Path.of(evidence.location().path()).isAbsolute()));
        assertTrue(result.evidences().stream().noneMatch(evidence -> evidence.summary().contains("private final")));
    }

    @Test
    void discoversMavenModulesAndCrossModuleReferences() {
        JavaScanResult result = scanner.scan(request("multi-module"));

        assertEquals(
                Set.of("module-api", "module-app", "module-infra"),
                result.artifacts().stream().map(artifact -> artifact.repositoryPath()).collect(Collectors.toSet()));
        assertTrue(hasDependency(
                result, "com.example.app.Application", "com.example.api.Port", DependencyKind.IMPLEMENTS));
        assertTrue(hasDependency(
                result,
                "com.example.app.Application",
                "com.example.infra.Repository",
                DependencyKind.DEPENDS_ON));
    }

    @Test
    void returnsStableDiagnosticForInvalidJava() {
        JavaScanResult first = scanner.scan(request("invalid-syntax"));
        JavaScanResult second = scanner.scan(request("invalid-syntax"));

        assertEquals(first, second);
        assertTrue(first.components().isEmpty());
        assertEquals(1, first.diagnostics().size());
        JavaParseDiagnostic diagnostic = first.diagnostics().getFirst();
        assertEquals("java.syntax.invalid", diagnostic.code());
        assertEquals("src/main/java/com/example/Broken.java", diagnostic.path());
        assertFalse(diagnostic.message().contains(fixture("invalid-syntax").toString()));
        assertFalse(diagnostic.message().contains("this is not valid Java"));
    }

    @Test
    void shuffledDiscoveryProducesTheSameResult() {
        JavaScanRequest request = request("multi-module");
        JavaSourceDiscovery discovery = new JavaSourceDiscovery();
        JavaSourceDiscovery.DiscoveryResult ordered = discovery.discover(request);
        ArrayList<DiscoveredJavaFile> reversedFiles = new ArrayList<>(ordered.files());
        Collections.reverse(reversedFiles);
        JavaSourceDiscovery.DiscoveryResult reversed = new JavaSourceDiscovery.DiscoveryResult(
                reversedFiles, ordered.diagnostics(), ordered.absoluteRoot(), ordered.realRoot());

        assertEquals(scanner.scanDiscovered(request, ordered), scanner.scanDiscovered(request, reversed));
    }

    @Test
    void enforcesFileCountFileSizeAndDepthLimits() {
        Path root = fixture("multi-module");
        JavaScanException fileCount = assertThrows(
                JavaScanException.class,
                () -> scanner.scan(new JavaScanRequest(
                        root, "multi-module", "Multi Module", "0.1.0", new JavaScanLimits(1, 1024, 4096, 32))));
        assertEquals("java.limit.files", fileCount.code());

        JavaScanException fileBytes = assertThrows(
                JavaScanException.class,
                () -> scanner.scan(new JavaScanRequest(
                        root, "multi-module", "Multi Module", "0.1.0", new JavaScanLimits(10, 16, 4096, 32))));
        assertEquals("java.limit.file-bytes", fileBytes.code());

        JavaScanException depth = assertThrows(
                JavaScanException.class,
                () -> scanner.scan(new JavaScanRequest(
                        root, "multi-module", "Multi Module", "0.1.0", new JavaScanLimits(10, 1024, 4096, 4))));
        assertEquals("java.limit.depth", depth.code());
    }

    @Test
    void rejectsUnsupportedContractVersion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new JavaScanRequest(
                        fixture("single-module"),
                        "single-module",
                        "Single Module",
                        "0.2.0",
                        JavaScanLimits.defaults()));

        assertEquals("schemaVersion must be 0.1.0", exception.getMessage());
    }

    @Test
    void rejectsInvalidUtf8WithoutEchoingSource(@TempDir Path repository) throws IOException {
        Path source = repository.resolve("src/main/java/example/BrokenEncoding.java");
        Files.createDirectories(source.getParent());
        Files.writeString(repository.resolve("pom.xml"), "<project/>");
        Files.write(source, new byte[] {(byte) 0xc3, (byte) 0x28});

        JavaScanRequest request = JavaScanRequest.forSchema010(repository, "encoding", "Encoding");
        assertEquals(1, new JavaSourceDiscovery().discover(request).files().size());
        JavaScanResult result = scanner.scan(request);

        assertEquals(1, result.diagnostics().size());
        assertEquals("java.encoding.invalid", result.diagnostics().getFirst().code());
        assertEquals("src/main/java/example/BrokenEncoding.java", result.diagnostics().getFirst().path());
        assertEquals("Java source is not valid UTF-8", result.diagnostics().getFirst().message());
    }

    @Test
    void acceptsTargetAsAJavaPackageDirectory(@TempDir Path repository) throws IOException {
        Path source = repository.resolve("src/main/java/example/target/TargetPackage.java");
        Files.createDirectories(source.getParent());
        Files.writeString(repository.resolve("pom.xml"), "<project/>");
        Files.writeString(source, "package example.target; public final class TargetPackage {}");

        JavaScanResult result = scanner.scan(JavaScanRequest.forSchema010(repository, "target-package", "Target Package"));

        assertEquals(
                Set.of("example.target.TargetPackage"),
                result.components().stream().map(Component::qualifiedName).collect(Collectors.toSet()));
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void skipsAllAmbiguousDuplicateQualifiedTypes(@TempDir Path repository) throws IOException {
        Path source = repository.resolve("src/main/java/example");
        Files.createDirectories(source);
        Files.writeString(repository.resolve("pom.xml"), "<project/>");
        Files.writeString(source.resolve("First.java"), "package example; class Duplicate {}");
        Files.writeString(source.resolve("Second.java"), "package example; class Duplicate {}");

        JavaScanResult result = scanner.scan(JavaScanRequest.forSchema010(repository, "duplicate", "Duplicate"));

        assertTrue(result.components().isEmpty());
        assertEquals(2, result.diagnostics().size());
        assertTrue(result.diagnostics().stream()
                .allMatch(diagnostic -> "java.type.duplicate".equals(diagnostic.code())));
    }

    @Test
    void skipsSymbolicJavaSourceThatLeavesTheRepository(@TempDir Path temporaryDirectory) throws IOException {
        Path repository = temporaryDirectory.resolve("repository");
        Path sourceDirectory = repository.resolve("src/main/java/example");
        Files.createDirectories(sourceDirectory);
        Files.writeString(repository.resolve("pom.xml"), "<project/>");
        Path outside = temporaryDirectory.resolve("Outside.java");
        Files.writeString(outside, "package example; public class Outside {}");
        Path link = sourceDirectory.resolve("Outside.java");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("symbolic links are unavailable on this test host");
        }

        JavaScanResult result = scanner.scan(JavaScanRequest.forSchema010(repository, "symlink", "Symlink"));

        assertTrue(result.components().isEmpty());
        assertEquals(1, result.diagnostics().size());
        assertEquals("java.path.symlink", result.diagnostics().getFirst().code());
        assertEquals("src/main/java/example/Outside.java", result.diagnostics().getFirst().path());
    }

    @Test
    void rejectsFileThatResolvesOutsideTheRepository(@TempDir Path temporaryDirectory) throws IOException {
        Path repository = temporaryDirectory.resolve("repository");
        Files.createDirectories(repository);
        Files.writeString(repository.resolve("pom.xml"), "<project/>");
        Path outside = temporaryDirectory.resolve("Outside.java");
        Files.writeString(outside, "package example; public class Outside {}");
        JavaScanRequest request = JavaScanRequest.forSchema010(repository, "escape", "Escape");
        JavaSourceDiscovery.DiscoveryResult escaped = new JavaSourceDiscovery.DiscoveryResult(
                List.of(new DiscoveredJavaFile(
                        outside, "src/main/java/example/Outside.java", ".", Files.size(outside))),
                List.of(),
                repository.toAbsolutePath().normalize(),
                repository.toRealPath());

        JavaScanResult result = scanner.scanDiscovered(request, escaped);

        assertTrue(result.components().isEmpty());
        assertEquals(1, result.diagnostics().size());
        assertEquals("java.path.escape", result.diagnostics().getFirst().code());
        assertFalse(result.diagnostics().getFirst().message().contains(temporaryDirectory.toString()));
    }

    private static boolean hasDependency(
            JavaScanResult result, String sourceName, String targetName, DependencyKind kind) {
        Map<String, String> namesById = result.components().stream()
                .collect(Collectors.toMap(Component::id, Component::qualifiedName));
        return result.dependencies().stream().anyMatch(dependency -> kind == dependency.kind()
                && sourceName.equals(namesById.get(dependency.sourceId()))
                && targetName.equals(namesById.get(dependency.targetId())));
    }

    private static JavaScanRequest request(String fixture) {
        return JavaScanRequest.forSchema010(fixture(fixture), fixture, fixture);
    }

    private static Path fixture(String name) {
        try {
            return Path.of(JavaSourceScannerTest.class.getResource("/fixtures/" + name).toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid fixture URI", exception);
        }
    }
}
