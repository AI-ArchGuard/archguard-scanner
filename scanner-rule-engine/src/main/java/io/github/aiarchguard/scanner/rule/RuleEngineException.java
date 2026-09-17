package io.github.aiarchguard.scanner.rule;

public final class RuleEngineException extends RuntimeException {

    private final String code;

    public RuleEngineException(String code, String safeMessage) {
        super(safeMessage);
        if (code == null || !code.matches("[a-z][a-z0-9.-]+")) {
            throw new IllegalArgumentException("rule engine error code must be namespaced");
        }
        this.code = code;
    }

    public String code() {
        return code;
    }
}
