package io.github.aiarchguard.scanner.domain.model;

import java.util.List;
import java.util.Map;

public record Artifact(
        String id,
        String projectId,
        ArtifactKind kind,
        String name,
        String language,
        String repositoryPath,
        String qualifiedName,
        Map<String, List<String>> extensions) {

    public Artifact {
        id = ModelValidation.requireId(id, EntityKind.ARTIFACT);
        projectId = ModelValidation.requireId(projectId, EntityKind.PROJECT);
        if (kind == null) {
            throw new IllegalArgumentException("artifact kind must not be null");
        }
        name = ModelValidation.requireText(name, "artifact name");
        language = ModelValidation.normalizeLanguage(language);
        repositoryPath = RepositoryPath.normalize(repositoryPath);
        qualifiedName = ModelValidation.requireText(qualifiedName, "artifact qualified name");
        extensions = ModelValidation.extensions(extensions);
    }
}
