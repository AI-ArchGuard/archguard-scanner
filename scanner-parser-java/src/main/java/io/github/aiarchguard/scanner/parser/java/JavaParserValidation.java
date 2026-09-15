package io.github.aiarchguard.scanner.parser.java;

import java.text.Normalizer;
import java.util.regex.Pattern;

final class JavaParserValidation {

    private static final Pattern NAMESPACED_CODE = Pattern.compile("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9-]*)+");

    private JavaParserValidation() {}

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (normalized.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.CONTROL)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return normalized;
    }

    static String requireNamespacedCode(String value) {
        String code = requireText(value, "diagnostic code");
        if (!NAMESPACED_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("diagnostic code must be namespaced");
        }
        return code;
    }
}
