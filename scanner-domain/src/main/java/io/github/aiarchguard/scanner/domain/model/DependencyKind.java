package io.github.aiarchguard.scanner.domain.model;

public enum DependencyKind {
    CONTAINS("contains"),
    DEPENDS_ON("depends-on"),
    IMPORTS("imports"),
    CALLS("calls"),
    READS("reads"),
    WRITES("writes"),
    EXTENDS("extends"),
    IMPLEMENTS("implements");

    private final String wireValue;

    DependencyKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static DependencyKind fromWireValue(String value) {
        for (DependencyKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown dependency kind: " + value);
    }
}
