package io.github.aiarchguard.scanner.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StableIdGeneratorTest {

    private static final List<Class<?>> DOMAIN_TYPES = List.of(
            Project.class,
            Artifact.class,
            Component.class,
            Dependency.class,
            Metric.class,
            Finding.class,
            Evidence.class,
            SourceLocation.class,
            RuleReference.class);

    @Test
    void identicalNormalizedInputsProduceIdenticalIdentifiers() {
        String first = StableIdGenerator.generate(
                "0.1.0",
                "example/order-service",
                EntityKind.COMPONENT,
                "JAVA",
                "src\\main\\Café.java",
                "example.order.Café");
        String second = StableIdGenerator.generate(
                "0.1.0",
                "example/order-service",
                EntityKind.COMPONENT,
                "java",
                "src/main/Cafe\u0301.java",
                "example.order.Cafe\u0301");

        assertEquals(first, second);
        assertTrue(first.matches("component_[0-9a-f]{64}"));
    }

    @Test
    void repositoryPathsAreNormalizedWithoutChangingSourceCase() {
        assertEquals("src/main/Order.java", RepositoryPath.normalize("src\\main\\Order.java"));
        assertEquals("src/main/Order.java", RepositoryPath.normalize("src/./main/Order.java"));
        String upperCase = StableIdGenerator.generate(
                "0.1.0", "example/order-service", EntityKind.ARTIFACT, "java", "Order", "Order");
        String lowerCase = StableIdGenerator.generate(
                "0.1.0", "example/order-service", EntityKind.ARTIFACT, "java", "order", "Order");
        assertTrue(!upperCase.equals(lowerCase), "repository identity is case-sensitive");
    }

    @Test
    void absoluteAndEscapingPathsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RepositoryPath.normalize("C:/work/Order.java"));
        assertThrows(IllegalArgumentException.class, () -> RepositoryPath.normalize("C:work/Order.java"));
        assertThrows(IllegalArgumentException.class, () -> RepositoryPath.normalize("/work/Order.java"));
        assertThrows(IllegalArgumentException.class, () -> RepositoryPath.normalize("src/../secrets.txt"));
    }

    @Test
    void extensionNamespacesFailClosedInsteadOfChangingCase() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Project(
                        "project_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "example/order-service",
                        "order-service",
                        "java",
                        ".",
                        Map.of("Java.modifiers", List.of("public"))));
    }

    @Test
    void publicModelDoesNotExposeLanguageOrBuildToolSpecificConcepts() {
        List<String> forbidden = List.of("javaclass", "javamethod", "mavenmodule", "gradleproject", "astnode");

        for (Class<?> type : DOMAIN_TYPES) {
            assertTrue(
                    type.getDeclaredAnnotations().length == 0,
                    () -> type.getSimpleName() + " must not carry serialization annotations");
            String surface = Stream.of(type.getRecordComponents())
                    .map(RecordComponent::getName)
                    .reduce(type.getSimpleName(), (left, right) -> left + " " + right)
                    .toLowerCase(Locale.ROOT);
            for (String concept : forbidden) {
                assertTrue(!surface.contains(concept), () -> type.getSimpleName() + " exposes " + concept);
            }
        }
    }

    @Test
    void evidenceHasNoSourceBodyField() {
        List<String> names = Stream.of(Evidence.class.getRecordComponents()).map(RecordComponent::getName).toList();
        assertTrue(names.stream().noneMatch(name -> List.of("source", "snippet", "content", "body").contains(name)));
    }
}
