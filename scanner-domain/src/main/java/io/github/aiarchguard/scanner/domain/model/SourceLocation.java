package io.github.aiarchguard.scanner.domain.model;

public record SourceLocation(String path, int startLine, int startColumn, int endLine, int endColumn) {

    public SourceLocation {
        path = RepositoryPath.normalize(path);
        if (startLine < 1 || startColumn < 1 || endLine < 1 || endColumn < 1) {
            throw new IllegalArgumentException("source location coordinates are 1-based");
        }
        if (endLine < startLine || (endLine == startLine && endColumn < startColumn)) {
            throw new IllegalArgumentException("source location end must not precede start");
        }
    }
}
