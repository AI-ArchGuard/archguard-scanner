package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ForbiddenComponentConfig(List<ForbiddenSelector> selectors, Severity severity)
        implements StructureRuleConfiguration {

    private static final RuleReference RULE = new RuleReference("archguard.forbidden-component", "0.1.0");

    public ForbiddenComponentConfig {
        if (selectors == null || selectors.isEmpty() || selectors.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("forbidden selectors must not be empty");
        }
        selectors = selectors.stream()
                .sorted(Comparator.comparing(ForbiddenSelector::canonicalValue))
                .toList();
        Set<String> values = new HashSet<>();
        if (selectors.stream().anyMatch(value -> !values.add(value.canonicalValue()))) {
            throw new IllegalArgumentException("forbidden selectors must be unique");
        }
        severity = RuleSeverity.requireAllowed(severity);
    }

    public ForbiddenComponentConfig(List<ForbiddenSelector> selectors) {
        this(selectors, Severity.HIGH);
    }

    @Override
    public RuleReference rule() {
        return RULE;
    }
}
