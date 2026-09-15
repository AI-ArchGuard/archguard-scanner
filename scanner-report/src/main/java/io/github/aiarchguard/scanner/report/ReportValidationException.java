package io.github.aiarchguard.scanner.report;

import java.util.List;

public final class ReportValidationException extends IllegalArgumentException {

    private final List<String> reasons;

    public ReportValidationException(List<String> reasons) {
        super(message(reasons));
        this.reasons = List.copyOf(reasons);
    }

    public List<String> reasons() {
        return reasons;
    }

    private static String message(List<String> reasons) {
        if (reasons == null || reasons.isEmpty() || reasons.stream().anyMatch(reason -> reason == null)) {
            throw new IllegalArgumentException("validation reasons must not be empty");
        }
        return "Scanner report validation failed: " + String.join("; ", reasons);
    }
}
