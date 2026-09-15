package io.github.aiarchguard.scanner.parser.java;

public final class JavaScanException extends RuntimeException {

    private final String code;

    JavaScanException(String code, String safeMessage) {
        super(safeMessage);
        this.code = JavaParserValidation.requireNamespacedCode(code);
    }

    public String code() {
        return code;
    }
}
