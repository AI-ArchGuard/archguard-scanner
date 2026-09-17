package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class IllegalPackageDependencyRule {

    List<Finding> evaluate(RuleInput input, IllegalPackageDependencyConfig config, int maxFindings) {
        Map<String, Component> components = input.componentIndex();
        List<Finding> findings = new ArrayList<>();
        for (Dependency dependency : input.dependencies()) {
            var sourcePackageValue = ComponentFacts.optionalPackageName(components.get(dependency.sourceId()));
            var targetPackageValue = ComponentFacts.optionalPackageName(components.get(dependency.targetId()));
            if (sourcePackageValue.isEmpty() || targetPackageValue.isEmpty()) {
                continue;
            }
            String sourcePackage = sourcePackageValue.orElseThrow();
            String targetPackage = targetPackageValue.orElseThrow();
            if (sourcePackage.equals(targetPackage)) {
                continue;
            }
            List<PackagePolicy> matchingPolicies = config.policies().stream()
                    .filter(policy -> policy.source().matches(sourcePackage))
                    .toList();
            if (matchingPolicies.isEmpty()) {
                continue;
            }
            boolean denied = matchingPolicies.stream().anyMatch(policy ->
                    policy.deniedTargets().stream().anyMatch(selector -> selector.matches(targetPackage)));
            boolean notAllowed = matchingPolicies.stream()
                    .filter(policy -> !policy.allowedTargets().isEmpty())
                    .anyMatch(policy -> policy.allowedTargets().stream()
                            .noneMatch(selector -> selector.matches(targetPackage)));
            if (denied || notAllowed) {
                requireCapacity(findings, maxFindings);
                String violation = denied ? "denied-package-dependency" : "unlisted-package-dependency";
                String qualifier = denied ? "is denied" : "is not allowed";
                findings.add(FindingFactory.edge(
                        input,
                        config.rule(),
                        config.severity(),
                        dependency,
                        violation,
                        "Package dependency from " + sourcePackage + " to " + targetPackage + " " + qualifier));
            }
        }
        return findings.stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }

    private static void requireCapacity(List<Finding> findings, int maxFindings) {
        if (findings.size() >= maxFindings) {
            throw new RuleEngineException("rule.limit.findings", "rule finding limit exceeded");
        }
    }
}
