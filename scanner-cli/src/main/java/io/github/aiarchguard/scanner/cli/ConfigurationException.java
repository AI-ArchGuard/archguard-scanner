package io.github.aiarchguard.scanner.cli;

final class ConfigurationException extends RuntimeException {

    private final String code;

    ConfigurationException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() {
        return code;
    }
}
