package io.github.aiarchguard.scanner.domain.model;

import java.util.List;
import java.util.Map;

public record Project(
        String id,
        String identity,
        String name,
        String language,
        String repositoryPath,
        Map<String, List<String>> extensions) {

    public Project {
        id = ModelValidation.requireId(id, EntityKind.PROJECT);
        identity = ModelValidation.requireText(identity, "project identity");
        name = ModelValidation.requireText(name, "project name");
        language = ModelValidation.normalizeLanguage(language);
        repositoryPath = RepositoryPath.normalize(repositoryPath);
        extensions = ModelValidation.extensions(extensions);
    }
}
