package io.github.aiarchguard.scanner.domain.model;

import java.util.List;
import java.util.Map;

public record Component(
        String id,
        String artifactId,
        ComponentKind kind,
        String name,
        String language,
        String qualifiedName,
        SourceLocation location,
        Map<String, List<String>> extensions) {

    public Component {
        id = ModelValidation.requireId(id, EntityKind.COMPONENT);
        artifactId = ModelValidation.requireId(artifactId, EntityKind.ARTIFACT);
        if (kind == null) {
            throw new IllegalArgumentException("component kind must not be null");
        }
        name = ModelValidation.requireText(name, "component name");
        language = ModelValidation.normalizeLanguage(language);
        qualifiedName = ModelValidation.requireText(qualifiedName, "component qualified name");
        extensions = ModelValidation.extensions(extensions);
    }
}
