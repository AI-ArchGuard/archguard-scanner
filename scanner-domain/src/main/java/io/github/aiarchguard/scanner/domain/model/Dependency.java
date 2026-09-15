package io.github.aiarchguard.scanner.domain.model;

import java.util.List;
import java.util.Map;

public record Dependency(
        String id,
        String sourceId,
        String targetId,
        DependencyKind kind,
        List<String> evidenceIds,
        Map<String, List<String>> extensions) {

    public Dependency {
        id = ModelValidation.requireId(id, EntityKind.DEPENDENCY);
        sourceId = ModelValidation.requireId(sourceId, EntityKind.COMPONENT);
        targetId = ModelValidation.requireId(targetId, EntityKind.COMPONENT);
        if (kind == null) {
            throw new IllegalArgumentException("dependency kind must not be null");
        }
        evidenceIds = ModelValidation.ids(evidenceIds, EntityKind.EVIDENCE, "dependency evidenceIds", true);
        extensions = ModelValidation.extensions(extensions);
    }
}
