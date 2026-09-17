package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record InternalModuleAccessConfig(List<ModuleBoundary> boundaries, Severity severity)
        implements StructureRuleConfiguration {

    private static final RuleReference RULE =
            new RuleReference("archguard.internal-module-access", "0.1.0");

    public InternalModuleAccessConfig {
        if (boundaries == null || boundaries.isEmpty() || boundaries.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("module boundaries must not be empty");
        }
        boundaries = boundaries.stream()
                .sorted(Comparator.comparing(ModuleBoundary::modulePath))
                .toList();
        Set<String> paths = new HashSet<>();
        if (boundaries.stream().anyMatch(value -> !paths.add(value.modulePath()))) {
            throw new IllegalArgumentException("module boundary paths must be unique");
        }
        severity = RuleSeverity.requireAllowed(severity);
    }

    public InternalModuleAccessConfig(List<ModuleBoundary> boundaries) {
        this(boundaries, Severity.HIGH);
    }

    @Override
    public RuleReference rule() {
        return RULE;
    }
}
