package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.ComponentKind;
import io.github.aiarchguard.scanner.domain.model.Finding;
import io.github.aiarchguard.scanner.domain.model.Metric;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ComplexityThresholdRule {

    List<Finding> evaluate(RuleInput input, ComplexityThresholdConfig config, int maxFindings) {
        Map<String, Component> components = input.componentIndex();
        Map<String, Artifact> artifacts = input.artifactIndex();
        List<Finding> findings = new ArrayList<>();
        for (Metric metric : input.metrics()) {
            if (!"complexity.cyclomatic".equals(metric.key())) {
                continue;
            }
            Integer threshold;
            String scope;
            if (components.containsKey(metric.scopeId())) {
                Component component = components.get(metric.scopeId());
                if (component.kind() == ComponentKind.FUNCTION) {
                    threshold = config.functionThreshold();
                    scope = "function";
                } else if (component.kind() == ComponentKind.TYPE) {
                    threshold = config.typeThreshold();
                    scope = "type";
                } else {
                    continue;
                }
            } else if (artifacts.containsKey(metric.scopeId())) {
                threshold = config.moduleThreshold();
                scope = "module";
            } else {
                continue;
            }
            if (threshold == null || metric.value().compareTo(java.math.BigDecimal.valueOf(threshold)) <= 0) {
                continue;
            }
            if (findings.size() >= maxFindings) {
                throw new RuleEngineException("rule.limit.findings", "rule finding limit exceeded");
            }
            String evidenceId = S5RuleFacts.metricEvidenceId(metric);
            findings.add(FindingFactory.custom(
                    input,
                    config.rule(),
                    config.severity(),
                    metric.scopeId(),
                    List.of(evidenceId),
                    "complexity-threshold-" + scope,
                    List.of(scope, metric.scopeId(), metric.value().toPlainString(), Integer.toString(threshold)),
                    "Cyclomatic complexity " + metric.value().toPlainString() + " exceeds " + scope
                            + " threshold " + threshold,
                    Map.of(
                            "archguard.metric", List.of(metric.key()),
                            "archguard.threshold", List.of(Integer.toString(threshold)))));
        }
        return findings.stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }
}
