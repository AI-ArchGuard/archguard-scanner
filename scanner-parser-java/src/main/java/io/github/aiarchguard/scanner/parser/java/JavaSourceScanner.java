package io.github.aiarchguard.scanner.parser.java;

public final class JavaSourceScanner {

    private final JavaSourceDiscovery discovery;
    private final JavaModelExtractor extractor;

    public JavaSourceScanner() {
        this(new JavaSourceDiscovery(), new JavaModelExtractor());
    }

    JavaSourceScanner(JavaSourceDiscovery discovery, JavaModelExtractor extractor) {
        this.discovery = discovery;
        this.extractor = extractor;
    }

    public JavaScanResult scan(JavaScanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return scanDiscovered(request, discovery.discover(request));
    }

    JavaScanResult scanDiscovered(JavaScanRequest request, JavaSourceDiscovery.DiscoveryResult discovered) {
        return extractor.extract(request, discovered);
    }
}
