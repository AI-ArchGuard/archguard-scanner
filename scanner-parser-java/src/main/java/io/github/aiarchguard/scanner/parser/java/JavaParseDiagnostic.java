package io.github.aiarchguard.scanner.parser.java;

import io.github.aiarchguard.scanner.domain.model.RepositoryPath;

public record JavaParseDiagnostic(String code, String path, int line, int column, String message)
        implements Comparable<JavaParseDiagnostic> {

    public JavaParseDiagnostic {
        code = JavaParserValidation.requireNamespacedCode(code);
        path = RepositoryPath.normalize(path);
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("diagnostic coordinates are 1-based");
        }
        message = JavaParserValidation.requireText(message, "diagnostic message");
    }

    @Override
    public int compareTo(JavaParseDiagnostic other) {
        int comparison = path.compareTo(other.path);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(line, other.line);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(column, other.column);
        if (comparison != 0) {
            return comparison;
        }
        comparison = code.compareTo(other.code);
        return comparison != 0 ? comparison : message.compareTo(other.message);
    }
}
