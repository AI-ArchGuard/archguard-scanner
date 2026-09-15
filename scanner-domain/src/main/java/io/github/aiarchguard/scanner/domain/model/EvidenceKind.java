package io.github.aiarchguard.scanner.domain.model;

public enum EvidenceKind {
    DECLARATION("declaration"),
    REFERENCE("reference"),
    RELATION("relation"),
    CONFIGURATION("configuration"),
    METRIC("metric");

    private final String wireValue;

    EvidenceKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static EvidenceKind fromWireValue(String value) {
        for (EvidenceKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown evidence kind: " + value);
    }
}
