package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record LayeredArchitectureConfig(
        List<ArchitectureLayer> layers, boolean allowLayerSkipping, Severity severity)
        implements StructureRuleConfiguration {

    private static final RuleReference RULE =
            new RuleReference("archguard.layered-architecture", "0.1.0");

    public LayeredArchitectureConfig {
        if (layers == null || layers.size() < 2 || layers.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("layered architecture requires at least two layers");
        }
        layers = List.copyOf(layers);
        Set<String> names = new HashSet<>();
        Set<String> selectors = new HashSet<>();
        for (ArchitectureLayer layer : layers) {
            if (!names.add(layer.name())) {
                throw new IllegalArgumentException("layer names must be unique");
            }
            for (PackageSelector selector : layer.packages()) {
                if (!selectors.add(selector.pattern())) {
                    throw new IllegalArgumentException("package selectors must belong to one layer only");
                }
            }
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
    }

    @Override
    public RuleReference rule() {
        return RULE;
    }
}
