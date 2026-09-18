package io.github.aiarchguard.scanner.cli;

import io.github.aiarchguard.scanner.domain.model.Severity;
import io.github.aiarchguard.scanner.parser.java.JavaScanLimits;
import io.github.aiarchguard.scanner.rule.RuleExecutionLimits;
import io.github.aiarchguard.scanner.rule.StructureRuleConfiguration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

record ScannerConfiguration(
        String projectIdentity,
        String projectName,
        Severity failOn,
        JavaScanLimits scanLimits,
        RuleExecutionLimits ruleLimits,
        long maxOutputBytes,
        List<StructureRuleConfiguration> rules) {

    static final long DEFAULT_MAX_OUTPUT_BYTES = 50L * 1024 * 1024;

    ScannerConfiguration {
        if (projectIdentity == null || projectIdentity.isBlank() || projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("project identity and name must not be blank");
        }
        if (failOn == null || scanLimits == null || ruleLimits == null) {
            throw new IllegalArgumentException("configuration values must not be null");
        }
        if (maxOutputBytes < 1024 || maxOutputBytes > 100L * 1024 * 1024) {
            throw new IllegalArgumentException("maxOutputBytes must be between 1024 and 104857600");
        }
        if (rules == null || rules.isEmpty() || rules.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("rules must not be empty");
        }
        rules = rules.stream()
                .sorted(Comparator.comparing(value -> value.rule().id()))
                .toList();
        Set<String> identifiers = new HashSet<>();
        if (rules.stream().anyMatch(rule -> !identifiers.add(rule.rule().id()))) {
            throw new IllegalArgumentException("each rule id may only be configured once");
        }
    }
}
