package io.github.aiarchguard.scanner.domain.model;

import java.util.List;
import java.util.Map;

public record Finding(
        String id,
        String fingerprint,
        RuleReference rule,
        Severity severity,
        String subjectId,
        String message,
        SourceLocation location,
        List<String> evidenceIds,
        Map<String, List<String>> extensions) {

    public Finding {
        id = ModelValidation.requireId(id, EntityKind.FINDING);
        fingerprint = ModelValidation.requireFingerprint(fingerprint);
        if (rule == null) {
            throw new IllegalArgumentException("finding rule must not be null");
        }
        if (severity == null) {
            throw new IllegalArgumentException("finding severity must not be null");
        }
        subjectId = ModelValidation.requireText(subjectId, "finding subjectId");
        message = ModelValidation.requireText(message, "finding message");
        evidenceIds = ModelValidation.ids(evidenceIds, EntityKind.EVIDENCE, "finding evidenceIds", true);
        extensions = ModelValidation.extensions(extensions);
    }
}
