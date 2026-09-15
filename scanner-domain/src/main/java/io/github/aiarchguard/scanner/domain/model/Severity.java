package io.github.aiarchguard.scanner.domain.model;

public enum Severity {
    INFO("info"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    private final String wireValue;

    Severity(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static Severity fromWireValue(String value) {
        for (Severity severity : values()) {
            if (severity.wireValue.equals(value)) {
                return severity;
            }
        }
        throw new IllegalArgumentException("Unknown severity: " + value);
    }
}
