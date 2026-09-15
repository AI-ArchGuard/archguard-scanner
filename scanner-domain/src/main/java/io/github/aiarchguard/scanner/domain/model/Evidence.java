package io.github.aiarchguard.scanner.domain.model;

import java.util.List;
import java.util.Map;

public record Evidence(
        String id,
        EvidenceKind kind,
        String summary,
        SourceLocation location,
        Map<String, List<String>> extensions) {

    public Evidence {
        id = ModelValidation.requireId(id, EntityKind.EVIDENCE);
        if (kind == null) {
            throw new IllegalArgumentException("evidence kind must not be null");
        }
        summary = ModelValidation.requireText(summary, "evidence summary");
        if (location == null) {
            throw new IllegalArgumentException("evidence location must not be null");
        }
        extensions = ModelValidation.extensions(extensions);
    }
}
