package io.github.aiarchguard.scanner.rule;

public enum CycleScope {
    COMPONENT("component"),
    PACKAGE("package"),
    MODULE("module");

    private final String wireValue;

    CycleScope(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
