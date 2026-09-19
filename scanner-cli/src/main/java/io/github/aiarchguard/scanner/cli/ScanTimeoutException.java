package io.github.aiarchguard.scanner.cli;

final class ScanTimeoutException extends RuntimeException {

    ScanTimeoutException() {
        super("scan exceeded configured duration");
    }
}
