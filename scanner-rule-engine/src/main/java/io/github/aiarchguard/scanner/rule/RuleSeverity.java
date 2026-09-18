package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Severity;

final class RuleSeverity {

    private RuleSeverity() {}

    static Severity requireAllowed(Severity severity) {
        if (severity == null || severity == Severity.INFO) {
            throw new IllegalArgumentException("severity must be between low and critical");
        }
        return severity;
    }
}
