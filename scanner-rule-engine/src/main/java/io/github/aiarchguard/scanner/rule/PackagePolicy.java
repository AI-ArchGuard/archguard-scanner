package io.github.aiarchguard.scanner.rule;

import java.util.List;
import java.util.TreeSet;

public record PackagePolicy(
        PackageSelector source, List<PackageSelector> allowedTargets, List<PackageSelector> deniedTargets) {

    public PackagePolicy {
        if (source == null) {
            throw new IllegalArgumentException("package policy source must not be null");
        }
        allowedTargets = sortedUnique(allowedTargets, "allowedTargets");
        deniedTargets = sortedUnique(deniedTargets, "deniedTargets");
        if (allowedTargets.isEmpty() && deniedTargets.isEmpty()) {
            throw new IllegalArgumentException("package policy must allow or deny at least one target");
        }
    }

    private static List<PackageSelector> sortedUnique(List<PackageSelector> values, String field) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        TreeSet<PackageSelector> sorted = new TreeSet<>(values);
        if (sorted.size() != values.size()) {
            throw new IllegalArgumentException(field + " must not contain duplicates");
        }
        return List.copyOf(sorted);
    }
}
