package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.ComponentKind;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record AnnotationRequirement(
        String annotationName,
        Set<ComponentKind> componentKinds,
        List<String> declarationKinds,
        List<PackageSelector> packages) {

    public AnnotationRequirement {
        if (annotationName == null || annotationName.isBlank() || !annotationName.contains(".")) {
            throw new IllegalArgumentException("required annotation must be qualified");
        }
        annotationName = annotationName.trim();
        if (componentKinds == null || componentKinds.isEmpty() || componentKinds.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("annotation component kinds must not be empty");
        }
        if (!componentKinds.stream().allMatch(kind -> kind == ComponentKind.TYPE || kind == ComponentKind.FUNCTION)) {
            throw new IllegalArgumentException("required annotations only support type and function components");
        }
        componentKinds = Set.copyOf(componentKinds);
        if (declarationKinds == null || declarationKinds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("declaration kinds must not contain blank values");
        }
        declarationKinds = declarationKinds.stream().distinct().sorted().toList();
        if (packages == null || packages.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("annotation packages must not contain null");
        }
        packages = packages.stream()
                .distinct()
                .sorted(Comparator.comparing(PackageSelector::pattern))
                .toList();
    }

    String canonicalValue() {
        return annotationName + "|" + componentKinds.stream().map(Enum::name).sorted().toList() + "|"
                + declarationKinds + "|" + packages.stream().map(PackageSelector::pattern).toList();
    }
}
