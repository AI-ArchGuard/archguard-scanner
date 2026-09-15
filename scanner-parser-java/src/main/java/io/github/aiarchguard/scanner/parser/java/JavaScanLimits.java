package io.github.aiarchguard.scanner.parser.java;

public record JavaScanLimits(int maxFiles, long maxFileBytes, long maxTotalBytes, int maxDepth) {

    public static final int DEFAULT_MAX_FILES = 10_000;
    public static final long DEFAULT_MAX_FILE_BYTES = 2L * 1024 * 1024;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    public static final int DEFAULT_MAX_DEPTH = 32;

    public JavaScanLimits {
        if (maxFiles < 1) {
            throw new IllegalArgumentException("maxFiles must be positive");
        }
        if (maxFileBytes < 1) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }
        if (maxTotalBytes < maxFileBytes) {
            throw new IllegalArgumentException("maxTotalBytes must be at least maxFileBytes");
        }
        if (maxDepth < 4) {
            throw new IllegalArgumentException("maxDepth must allow a Maven source layout");
        }
        if (maxDepth > 256) {
            throw new IllegalArgumentException("maxDepth must not exceed 256");
        }
    }

    public static JavaScanLimits defaults() {
        return new JavaScanLimits(
                DEFAULT_MAX_FILES, DEFAULT_MAX_FILE_BYTES, DEFAULT_MAX_TOTAL_BYTES, DEFAULT_MAX_DEPTH);
    }
}
