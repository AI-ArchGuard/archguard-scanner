package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RepositoryPath;
import java.util.Comparator;
import java.util.List;

public record ModuleBoundary(String modulePath, List<PackageSelector> publicPackages) {

    public ModuleBoundary {
        modulePath = RepositoryPath.normalize(modulePath);
        if (publicPackages == null || publicPackages.isEmpty() || publicPackages.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("module public packages must not be empty");
        }
        publicPackages = publicPackages.stream()
                .distinct()
                .sorted(Comparator.comparing(PackageSelector::pattern))
                .toList();
    }
}
