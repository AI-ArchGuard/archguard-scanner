package io.github.aiarchguard.scanner.domain.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public final class RepositoryPath {

    private RepositoryPath() {}

    public static String normalize(String value) {
        String path = ModelValidation.requireText(value, "repository path");
        path = Normalizer.normalize(path, Normalizer.Form.NFC).replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("repository path must be relative: " + value);
        }

        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("repository path must not contain empty segments: " + value);
            }
            if (".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("repository path must not escape the repository: " + value);
            }
            segments.add(segment);
        }
        return segments.isEmpty() ? "." : String.join("/", segments);
    }
}
