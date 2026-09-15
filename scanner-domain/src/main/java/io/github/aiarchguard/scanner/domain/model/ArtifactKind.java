package io.github.aiarchguard.scanner.domain.model;

public enum ArtifactKind {
    MODULE("module"),
    SOURCE_SET("source-set"),
    PACKAGE("package"),
    NAMESPACE("namespace"),
    LIBRARY("library");

    private final String wireValue;

    ArtifactKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ArtifactKind fromWireValue(String value) {
        for (ArtifactKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown artifact kind: " + value);
    }
}
