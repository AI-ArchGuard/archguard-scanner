package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Component;
import java.util.List;
import java.util.Optional;

final class ComponentFacts {

    private ComponentFacts() {}

    static String packageName(Component component) {
        return optionalPackageName(component).orElseThrow(() -> new RuleEngineException(
                "rule.input.missing-package", "component must contain exactly one java.package value"));
    }

    static Optional<String> optionalPackageName(Component component) {
        List<String> values = component.extensions().get("java.package");
        if (values == null) {
            return Optional.empty();
        }
        if (values.size() != 1) {
            throw new RuleEngineException(
                    "rule.input.missing-package", "component must contain exactly one java.package value");
        }
        return Optional.of(values.getFirst());
    }
}
