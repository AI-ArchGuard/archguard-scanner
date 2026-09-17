package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Component;
import java.util.List;

final class ComponentFacts {

    private ComponentFacts() {}

    static String packageName(Component component) {
        List<String> values = component.extensions().get("java.package");
        if (values == null || values.size() != 1) {
            throw new RuleEngineException(
                    "rule.input.missing-package", "component must contain exactly one java.package value");
        }
        return values.getFirst();
    }
}
