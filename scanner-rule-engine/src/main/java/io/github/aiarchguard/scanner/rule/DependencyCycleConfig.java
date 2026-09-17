package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;

public record DependencyCycleConfig(CycleScope scope, Severity severity)
        implements StructureRuleConfiguration {

    private static final RuleReference RULE = new RuleReference("archguard.dependency-cycle", "0.1.0");

    public DependencyCycleConfig {
        if (scope == null || severity == null) {
            throw new IllegalArgumentException("cycle scope and severity must not be null");
        }
    }

    @Override
    public RuleReference rule() {
        return RULE;
    }
}
