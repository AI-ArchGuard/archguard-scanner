package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.List;

public record RuleEngineResult(List<Finding> findings, List<RuleDiagnostic> diagnostics) {

    public RuleEngineResult {
        if (findings == null || findings.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("findings must not contain null");
        }
        findings = findings.stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
        if (diagnostics == null || diagnostics.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("diagnostics must not contain null");
        }
        diagnostics = diagnostics.stream().distinct().sorted().toList();
    }

    public RuleEngineResult(List<Finding> findings) {
        this(findings, List.of());
    }
}
