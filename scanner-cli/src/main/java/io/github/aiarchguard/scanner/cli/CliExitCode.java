package io.github.aiarchguard.scanner.cli;

public enum CliExitCode {
    SUCCESS(0),
    POLICY_VIOLATION(2),
    INVALID_INPUT(64),
    SCAN_FAILURE(70);

    private final int value;

    CliExitCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
