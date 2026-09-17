package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Metric;
import java.util.List;
import java.util.Optional;

final class S5RuleFacts {

    private S5RuleFacts() {}

    static boolean hasAnnotation(Component component, String annotationName) {
        return component.extensions().getOrDefault("java.annotations", List.of()).contains(annotationName);
    }

    static boolean annotationResolutionComplete(Component component) {
        return component.extensions().getOrDefault("java.annotation-resolution", List.of("complete"))
                .equals(List.of("complete"));
    }

    static String declarationKind(Component component) {
        return single(component.extensions().get("java.declaration-kind"), "java.declaration-kind", component.id());
    }

    static String declarationEvidenceId(Component component) {
        return single(
                component.extensions().get("archguard.declaration-evidence-id"),
                "archguard.declaration-evidence-id",
                component.id());
    }

    static String metricEvidenceId(Metric metric) {
        return single(
                metric.extensions().get("archguard.evidence-id"), "archguard.evidence-id", metric.id());
    }

    static Optional<String> mavenCoordinate(Artifact artifact) {
        List<String> values = artifact.extensions().get("maven.coordinate");
        if (values == null) {
            return Optional.empty();
        }
        if (values.size() != 1) {
            throw new RuleEngineException(
                    "rule.input.invalid-maven-coordinate", "artifact Maven coordinate must be single-valued");
        }
        return Optional.of(values.getFirst());
    }

    private static String single(List<String> values, String fact, String subjectId) {
        if (values == null || values.size() != 1) {
            throw new RuleEngineException("rule.input.missing-fact", subjectId + " must contain one " + fact);
        }
        return values.getFirst();
    }
}
