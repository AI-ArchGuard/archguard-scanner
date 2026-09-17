package io.github.aiarchguard.scanner.rule;

import java.text.Normalizer;
import java.util.List;
import java.util.TreeSet;

public record ArchitectureLayer(String name, List<PackageSelector> packages) {

    public ArchitectureLayer {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("layer name must not be blank");
        }
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        if (name.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.CONTROL)) {
            throw new IllegalArgumentException("layer name must not contain control characters");
        }
        if (packages == null || packages.isEmpty() || packages.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("layer packages must not be empty");
        }
        TreeSet<PackageSelector> sorted = new TreeSet<>(packages);
        if (sorted.size() != packages.size()) {
            throw new IllegalArgumentException("layer packages must not contain duplicates");
        }
        packages = List.copyOf(sorted);
    }
}
