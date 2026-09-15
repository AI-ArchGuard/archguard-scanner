package io.github.aiarchguard.scanner.domain.model;

public enum ComponentKind {
    MODULE("module"),
    NAMESPACE("namespace"),
    TYPE("type"),
    FUNCTION("function"),
    RESOURCE("resource"),
    DATA_STORE("data-store");

    private final String wireValue;

    ComponentKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ComponentKind fromWireValue(String value) {
        for (ComponentKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown component kind: " + value);
    }
}
