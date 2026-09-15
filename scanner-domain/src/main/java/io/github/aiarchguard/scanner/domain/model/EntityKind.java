package io.github.aiarchguard.scanner.domain.model;

public enum EntityKind {
    PROJECT("project"),
    ARTIFACT("artifact"),
    COMPONENT("component"),
    DEPENDENCY("dependency"),
    METRIC("metric"),
    FINDING("finding"),
    EVIDENCE("evidence");

    private final String wireValue;

    EntityKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
