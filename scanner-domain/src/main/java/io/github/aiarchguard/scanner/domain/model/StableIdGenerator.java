package io.github.aiarchguard.scanner.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;

public final class StableIdGenerator {

    private StableIdGenerator() {}

    public static String generate(
            String schemaVersion,
            String projectIdentity,
            EntityKind entityKind,
            String language,
            String repositoryRelativePath,
            String qualifiedName) {
        if (entityKind == null) {
            throw new IllegalArgumentException("entityKind must not be null");
        }
        String normalizedVersion = normalize(schemaVersion, "schemaVersion");
        String normalizedProject = normalize(projectIdentity, "projectIdentity");
        String normalizedLanguage = ModelValidation.normalizeLanguage(language);
        String normalizedPath = RepositoryPath.normalize(repositoryRelativePath);
        String normalizedName = normalize(qualifiedName, "qualifiedName");

        String canonicalInput = String.join(
                "\n",
                normalizedVersion,
                normalizedProject,
                entityKind.wireValue(),
                normalizedLanguage,
                normalizedPath,
                normalizedName);
        return entityKind.wireValue() + "_" + sha256(canonicalInput);
    }

    public static String fingerprint(String... normalizedParts) {
        if (normalizedParts == null || normalizedParts.length == 0) {
            throw new IllegalArgumentException("fingerprint requires at least one input");
        }
        String[] parts = new String[normalizedParts.length];
        for (int index = 0; index < normalizedParts.length; index++) {
            parts[index] = normalize(normalizedParts[index], "fingerprint input");
        }
        return sha256(String.join("\n", parts));
    }

    private static String normalize(String value, String field) {
        return Normalizer.normalize(ModelValidation.requireText(value, field), Normalizer.Form.NFC);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
