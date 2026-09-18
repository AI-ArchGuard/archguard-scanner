package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;

public record ComplexityThresholdConfig(
        Integer functionThreshold, Integer typeThreshold, Integer moduleThreshold, Severity severity)
        implements StructureRuleConfiguration {

    private static final RuleReference RULE = new RuleReference("archguard.complexity-threshold", "0.1.0");

    public ComplexityThresholdConfig {
        if (functionThreshold == null && typeThreshold == null && moduleThreshold == null) {
            throw new IllegalArgumentException("at least one complexity threshold is required");
        }
        requirePositive(functionThreshold, "functionThreshold");
        requirePositive(typeThreshold, "typeThreshold");
        requirePositive(moduleThreshold, "moduleThreshold");
        severity = RuleSeverity.requireAllowed(severity);
    }

    public ComplexityThresholdConfig(Integer functionThreshold, Integer typeThreshold, Integer moduleThreshold) {
        this(functionThreshold, typeThreshold, moduleThreshold, Severity.MEDIUM);
    }

    private static void requirePositive(Integer value, String name) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    @Override
    public RuleReference rule() {
        return RULE;
    }
}
