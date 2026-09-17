package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record RequiredAnnotationConfig(List<AnnotationRequirement> requirements, Severity severity)
        implements StructureRuleConfiguration {

    private static final RuleReference RULE = new RuleReference("archguard.required-annotation", "0.1.0");

    public RequiredAnnotationConfig {
        if (requirements == null || requirements.isEmpty() || requirements.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("annotation requirements must not be empty");
        }
        requirements = requirements.stream()
                .sorted(Comparator.comparing(AnnotationRequirement::canonicalValue))
                .toList();
        Set<String> values = new HashSet<>();
        if (requirements.stream().anyMatch(value -> !values.add(value.canonicalValue()))) {
            throw new IllegalArgumentException("annotation requirements must be unique");
        }
        severity = RuleSeverity.requireAllowed(severity);
    }

    public RequiredAnnotationConfig(List<AnnotationRequirement> requirements) {
        this(requirements, Severity.MEDIUM);
    }

    @Override
    public RuleReference rule() {
        return RULE;
    }
}
