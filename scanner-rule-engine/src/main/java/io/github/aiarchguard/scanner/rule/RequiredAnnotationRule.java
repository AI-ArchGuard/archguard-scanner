package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RequiredAnnotationRule {

    Evaluation evaluate(RuleInput input, RequiredAnnotationConfig config, int maxFindings) {
        List<Finding> findings = new ArrayList<>();
        List<RuleDiagnostic> diagnostics = new ArrayList<>();
        for (Component component : input.components()) {
            for (AnnotationRequirement requirement : config.requirements()) {
                if (!applies(component, requirement)) {
                    continue;
                }
                if (!S5RuleFacts.annotationResolutionComplete(component)) {
                    diagnostics.add(new RuleDiagnostic(
                            "rule.annotation.unresolved",
                            component.id(),
                            "required annotation could not be evaluated because annotation resolution is incomplete"));
                    continue;
                }
                if (S5RuleFacts.hasAnnotation(component, requirement.annotationName())) {
                    continue;
                }
                if (findings.size() >= maxFindings) {
                    throw new RuleEngineException("rule.limit.findings", "rule finding limit exceeded");
                }
                findings.add(FindingFactory.custom(
                        input,
                        config.rule(),
                        config.severity(),
                        component.id(),
                        List.of(S5RuleFacts.declarationEvidenceId(component)),
                        "required-annotation",
                        List.of(component.id(), requirement.annotationName()),
                        "Component " + component.qualifiedName() + " requires annotation "
                                + requirement.annotationName(),
                        Map.of("archguard.required-annotation", List.of(requirement.annotationName()))));
            }
        }
        return new Evaluation(findings, diagnostics);
    }

    private static boolean applies(Component component, AnnotationRequirement requirement) {
        if (!requirement.componentKinds().contains(component.kind())) {
            return false;
        }
        if (!requirement.declarationKinds().isEmpty()
                && !requirement.declarationKinds().contains(S5RuleFacts.declarationKind(component))) {
            return false;
        }
        if (requirement.packages().isEmpty()) {
            return true;
        }
        return ComponentFacts.optionalPackageName(component)
                .map(packageName -> requirement.packages().stream().anyMatch(selector -> selector.matches(packageName)))
                .orElse(false);
    }

    record Evaluation(List<Finding> findings, List<RuleDiagnostic> diagnostics) {}
}
