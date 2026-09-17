package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record IllegalPackageDependencyConfig(List<PackagePolicy> policies, Severity severity)
        implements StructureRuleConfiguration {

    private static final RuleReference RULE =
            new RuleReference("archguard.illegal-package-dependency", "0.1.0");

    public IllegalPackageDependencyConfig {
        if (policies == null || policies.isEmpty() || policies.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("package policies must not be empty");
        }
        policies = policies.stream()
                .sorted(Comparator.comparing(policy -> policy.source().pattern()))
                .toList();
        Set<String> sources = new HashSet<>();
        if (policies.stream().anyMatch(policy -> !sources.add(policy.source().pattern()))) {
            throw new IllegalArgumentException("package policy sources must be unique");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
    }

    @Override
    public RuleReference rule() {
        return RULE;
    }
}
