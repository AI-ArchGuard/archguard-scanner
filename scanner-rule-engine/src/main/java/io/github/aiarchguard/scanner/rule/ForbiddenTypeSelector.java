package io.github.aiarchguard.scanner.rule;

public record ForbiddenTypeSelector(String qualifiedName) implements ForbiddenSelector {

    public ForbiddenTypeSelector {
        if (qualifiedName == null || qualifiedName.isBlank() || !qualifiedName.contains(".")) {
            throw new IllegalArgumentException("forbidden type must be qualified");
        }
        qualifiedName = qualifiedName.trim();
    }

    @Override
    public String canonicalValue() {
        return "type:" + qualifiedName;
    }
}
