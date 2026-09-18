package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.ComponentKind;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ControllerRepositoryAccessRule {

    private static final Set<String> CONTROLLERS = Set.of(
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController");
    private static final String REPOSITORY = "org.springframework.stereotype.Repository";

    List<Finding> evaluate(RuleInput input, ControllerRepositoryAccessConfig config, int maxFindings) {
        Map<String, Component> components = input.componentIndex();
        List<Finding> findings = new ArrayList<>();
        for (Dependency dependency : input.dependencies()) {
            Component source = components.get(dependency.sourceId());
            Component target = components.get(dependency.targetId());
            if (source.kind() != ComponentKind.TYPE || target.kind() != ComponentKind.TYPE) {
                continue;
            }
            boolean controller = CONTROLLERS.stream().anyMatch(value -> S5RuleFacts.hasAnnotation(source, value));
            if (!controller || !S5RuleFacts.hasAnnotation(target, REPOSITORY)) {
                continue;
            }
            requireCapacity(findings, maxFindings);
            findings.add(FindingFactory.edge(
                    input,
                    config.rule(),
                    config.severity(),
                    dependency,
                    "controller-repository-access",
                    "Spring Controller " + source.qualifiedName() + " must not directly access Repository "
                            + target.qualifiedName()));
        }
        return findings.stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }

    private static void requireCapacity(List<Finding> findings, int maxFindings) {
        if (findings.size() >= maxFindings) {
            throw new RuleEngineException("rule.limit.findings", "rule finding limit exceeded");
        }
    }
}
