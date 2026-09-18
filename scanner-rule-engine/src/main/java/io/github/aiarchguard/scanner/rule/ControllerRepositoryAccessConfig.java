package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;

public record ControllerRepositoryAccessConfig(Severity severity) implements StructureRuleConfiguration {

    private static final RuleReference RULE =
            new RuleReference("spring.controller-repository-access", "0.1.0");

    public ControllerRepositoryAccessConfig {
        severity = RuleSeverity.requireAllowed(severity);
    }

    public ControllerRepositoryAccessConfig() {
        this(Severity.HIGH);
    }

    @Override
    public RuleReference rule() {
        return RULE;
    }
}
