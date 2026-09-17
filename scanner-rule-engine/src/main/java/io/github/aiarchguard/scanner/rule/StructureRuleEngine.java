package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StructureRuleEngine {

    private final IllegalPackageDependencyRule illegalPackageDependencyRule = new IllegalPackageDependencyRule();
    private final LayeredArchitectureRule layeredArchitectureRule = new LayeredArchitectureRule();
    private final DependencyCycleRule dependencyCycleRule = new DependencyCycleRule();

    public RuleEngineResult execute(
            RuleInput input,
            List<StructureRuleConfiguration> configurations,
            RuleExecutionLimits limits) {
        if (input == null || configurations == null || limits == null
                || configurations.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("rule execution input must not contain null");
        }
        Set<String> ruleIds = new HashSet<>();
        for (StructureRuleConfiguration configuration : configurations) {
            if (!ruleIds.add(configuration.rule().id())) {
                throw new RuleEngineException(
                        "rule.config.duplicate-rule", "a structure rule may only be configured once");
            }
        }
        DependencyGraph.create(input.components(), input.dependencies(), limits);

        List<Finding> findings = new ArrayList<>();
        configurations.stream()
                .sorted((left, right) -> left.rule().id().compareTo(right.rule().id()))
                .forEach(configuration -> {
                    int remainingFindings = limits.maxFindings() - findings.size();
                    List<Finding> produced = switch (configuration) {
                        case IllegalPackageDependencyConfig value ->
                            illegalPackageDependencyRule.evaluate(input, value, remainingFindings);
                        case LayeredArchitectureConfig value ->
                            layeredArchitectureRule.evaluate(input, value, remainingFindings);
                        case DependencyCycleConfig value ->
                            dependencyCycleRule.evaluate(input, value, remainingFindings);
                    };
                    if (findings.size() > limits.maxFindings() - produced.size()) {
                        throw new RuleEngineException(
                                "rule.limit.findings", "rule finding limit exceeded");
                    }
                    findings.addAll(produced);
                });
        return new RuleEngineResult(findings);
    }
}
