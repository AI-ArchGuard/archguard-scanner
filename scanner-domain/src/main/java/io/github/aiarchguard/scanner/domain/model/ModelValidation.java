package io.github.aiarchguard.scanner.domain.model;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

final class ModelValidation {

    private static final Pattern LANGUAGE = Pattern.compile("[a-z][a-z0-9+.-]*");
    private static final Pattern EXTENSION_KEY =
            Pattern.compile("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9_-]*)+");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private ModelValidation() {}

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (normalized.codePoints().anyMatch(ModelValidation::isControl)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return normalized;
    }

    static String requireId(String value, EntityKind kind) {
        String id = requireText(value, "id");
        String prefix = kind.wireValue() + "_";
        if (!id.startsWith(prefix) || !SHA_256.matcher(id.substring(prefix.length())).matches()) {
            throw new IllegalArgumentException("id must match " + prefix + " followed by a lowercase SHA-256 digest");
        }
        return id;
    }

    static String requireFingerprint(String value) {
        String fingerprint = requireText(value, "fingerprint");
        if (!SHA_256.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("fingerprint must be a lowercase SHA-256 digest");
        }
        return fingerprint;
    }

    static String normalizeLanguage(String value) {
        String language = requireText(value, "language").toLowerCase(Locale.ROOT);
        if (!LANGUAGE.matcher(language).matches()) {
            throw new IllegalArgumentException("language must use a lowercase language identifier");
        }
        return language;
    }

    static String optionalText(String value, String field) {
        return value == null ? null : requireText(value, field);
    }

    static List<String> ids(List<String> values, EntityKind kind, String field, boolean requireNonEmpty) {
        if (values == null || (requireNonEmpty && values.isEmpty())) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            copy.add(requireId(value, kind));
        }
        Collections.sort(copy);
        rejectDuplicates(copy, field);
        return List.copyOf(copy);
    }

    static Map<String, List<String>> extensions(Map<String, List<String>> extensions) {
        if (extensions == null) {
            throw new IllegalArgumentException("extensions must not be null");
        }
        Map<String, List<String>> sorted = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : extensions.entrySet()) {
            String key = requireText(entry.getKey(), "extension key");
            if (!EXTENSION_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("extension key must be namespaced: " + key);
            }
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("extension values must not be empty: " + key);
            }
            List<String> normalizedValues = new ArrayList<>(values.size());
            for (String value : values) {
                normalizedValues.add(requireText(value, "extension value"));
            }
            Collections.sort(normalizedValues);
            rejectDuplicates(normalizedValues, "extension values for " + key);
            sorted.put(key, List.copyOf(normalizedValues));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    static BigDecimal decimal(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("metric value must not be null");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private static void rejectDuplicates(List<String> values, String field) {
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).equals(values.get(index))) {
                throw new IllegalArgumentException(field + " must not contain duplicates");
            }
        }
    }

    private static boolean isControl(int codePoint) {
        return Character.getType(codePoint) == Character.CONTROL;
    }
}
