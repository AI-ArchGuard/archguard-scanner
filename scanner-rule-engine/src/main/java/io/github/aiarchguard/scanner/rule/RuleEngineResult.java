package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.List;

public record RuleEngineResult(List<Finding> findings) {

    public RuleEngineResult {
        if (findings == null || findings.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("findings must not contain null");
        }
        findings = findings.stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }
}
