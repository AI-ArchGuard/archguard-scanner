package io.github.aiarchguard.scanner.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aiarchguard.scanner.domain.model.Project;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReportContractTest {

    private final ReportJson reportJson = new ReportJson();

    @Test
    void publishedSchemaIsValidDraft202012() {
        assertDoesNotThrow(() -> new ReportSchemaValidator().validateSchema());
    }

    @ParameterizedTest
    @MethodSource("validFixtures")
    void validFixturesPass(String fixture) {
        assertDoesNotThrow(() -> reportJson.validate(resource(fixture)));
    }

    @ParameterizedTest
    @MethodSource("invalidFixtures")
    void invalidFixturesFailWithAnExplicitReason(String fixture, String expectedReasonToken) {
        ReportValidationException exception =
                assertThrows(ReportValidationException.class, () -> reportJson.validate(resource(fixture)));

        assertFalse(exception.reasons().isEmpty());
        assertTrue(
                exception.getMessage().toLowerCase(Locale.ROOT).contains(expectedReasonToken),
                () -> fixture + " returned: " + exception.getMessage());
    }

    @Test
    void serializationRoundTripPreservesEveryDomainField() {
        DomainReport original = reportJson.deserialize(resource("fixtures/valid/full-report.json"));

        DomainReport roundTripped = reportJson.deserialize(reportJson.serialize(original));

        assertEquals(original, roundTripped);
    }

    @Test
    void shuffledCollectionsProduceIdenticalJsonBytes() {
        DomainReport ordered = reportJson.deserialize(resource("fixtures/valid/full-report.json"));
        DomainReport shuffled = new DomainReport(
                ordered.project(),
                reversed(ordered.artifacts()),
                reversed(ordered.components()),
                reversed(ordered.dependencies()),
                reversed(ordered.metrics()),
                reversed(ordered.findings()),
                reversed(ordered.evidences()));

        assertArrayEquals(reportJson.serialize(ordered), reportJson.serialize(shuffled));
    }

    @Test
    void shuffledExtensionMapsAndValuesProduceIdenticalJsonBytes() {
        DomainReport report = reportJson.deserialize(resource("fixtures/valid/full-report.json"));
        Map<String, List<String>> first = new LinkedHashMap<>();
        first.put("zeta.flags", List.of("second", "first"));
        first.put("alpha.tags", List.of("stable"));
        Map<String, List<String>> second = new LinkedHashMap<>();
        second.put("alpha.tags", List.of("stable"));
        second.put("zeta.flags", List.of("first", "second"));

        assertArrayEquals(
                reportJson.serialize(withProject(report, projectWithExtensions(report.project(), first))),
                reportJson.serialize(withProject(report, projectWithExtensions(report.project(), second))));
    }

    @Test
    void schemaContainsNoLanguageSpecificCoreField() {
        String schema = reportJson.schema().toLowerCase(Locale.ROOT);

        assertFalse(schema.contains("javaclass"));
        assertFalse(schema.contains("javamethod"));
        assertFalse(schema.contains("mavenmodule"));
        assertFalse(schema.contains("astnode"));
        assertTrue(schema.contains("extensions"));
    }

    @Test
    void canonicalOutputNeverIntroducesAHostAbsolutePath() {
        String json = new String(
                reportJson.serialize(reportJson.deserialize(resource("fixtures/valid/full-report.json"))),
                StandardCharsets.UTF_8);

        assertFalse(json.matches(".*[A-Za-z]:[/\\\\].*"));
        assertFalse(json.contains("\\\\Users\\\\"));
    }

    private static Stream<String> validFixtures() {
        return Stream.of("fixtures/valid/minimal-report.json", "fixtures/valid/full-report.json");
    }

    private static Stream<Arguments> invalidFixtures() {
        return Stream.of(
                Arguments.of("fixtures/invalid/missing-required-field.json", "required"),
                Arguments.of("fixtures/invalid/invalid-version.json", "const"),
                Arguments.of("fixtures/invalid/invalid-enum.json", "enum"),
                Arguments.of("fixtures/invalid/invalid-location-range.json", "end"),
                Arguments.of("fixtures/invalid/unknown-field.json", "additional properties"),
                Arguments.of("fixtures/invalid/invalid-extension.json", "pattern"),
                Arguments.of("fixtures/invalid/invalid-identifier.json", "regex pattern"),
                Arguments.of("fixtures/invalid/dangling-reference.json", "unknown artifact"),
                Arguments.of("fixtures/invalid/absolute-path.json", "regex pattern"));
    }

    private static byte[] resource(String path) {
        try (InputStream input = ReportContractTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test resource: " + path);
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read test resource: " + path, exception);
        }
    }

    private static <T> List<T> reversed(List<T> values) {
        List<T> copy = new ArrayList<>(values);
        java.util.Collections.reverse(copy);
        return List.copyOf(copy);
    }

    private static Project projectWithExtensions(Project project, Map<String, List<String>> extensions) {
        return new Project(
                project.id(),
                project.identity(),
                project.name(),
                project.language(),
                project.repositoryPath(),
                extensions);
    }

    private static DomainReport withProject(DomainReport report, Project project) {
        return new DomainReport(
                project,
                report.artifacts(),
                report.components(),
                report.dependencies(),
                report.metrics(),
                report.findings(),
                report.evidences());
    }
}
