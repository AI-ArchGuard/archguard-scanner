package io.github.aiarchguard.scanner.rule;

public record RuleDiagnostic(String code, String subjectId, String message) implements Comparable<RuleDiagnostic> {

    public RuleDiagnostic {
        if (code == null || !code.matches("^[a-z][a-z0-9]*(?:[.-][a-z0-9][a-z0-9-]*)+$")) {
            throw new IllegalArgumentException("diagnostic code must be namespaced");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("diagnostic subjectId must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("diagnostic message must not be blank");
        }
    }

    @Override
    public int compareTo(RuleDiagnostic other) {
        int comparison = code.compareTo(other.code);
        if (comparison != 0) {
            return comparison;
        }
        comparison = subjectId.compareTo(other.subjectId);
        return comparison != 0 ? comparison : message.compareTo(other.message);
    }
}
