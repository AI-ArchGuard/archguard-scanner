package io.github.aiarchguard.scanner.domain.model;

import java.util.regex.Pattern;

public record RuleReference(String id, String version) {

    private static final Pattern RULE_ID = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9][a-z0-9-]*)+");
    private static final Pattern VERSION =
            Pattern.compile("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");

    public RuleReference {
        id = ModelValidation.requireText(id, "rule id");
        version = ModelValidation.requireText(version, "rule version");
        if (!RULE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("rule id must be a namespaced identifier");
        }
        if (!VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("rule version must be semantic version core syntax");
        }
    }
}
