package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class LayeredArchitectureRule {

    List<Finding> evaluate(RuleInput input, LayeredArchitectureConfig config, int maxFindings) {
        Map<String, Component> components = input.componentIndex();
        List<Finding> findings = new ArrayList<>();
        for (Dependency dependency : input.dependencies()) {
            Component source = components.get(dependency.sourceId());
            Component target = components.get(dependency.targetId());
            int sourceLayer = layerIndex(source, config);
            int targetLayer = layerIndex(target, config);
            if (sourceLayer < 0 || targetLayer < 0 || sourceLayer == targetLayer) {
                continue;
            }
            String violation = null;
            if (targetLayer < sourceLayer) {
                violation = "reverse-layer-dependency";
            } else if (!config.allowLayerSkipping() && targetLayer > sourceLayer + 1) {
                violation = "skipped-layer-dependency";
            }
            if (violation != null) {
                if (findings.size() >= maxFindings) {
                    throw new RuleEngineException("rule.limit.findings", "rule finding limit exceeded");
                }
                findings.add(FindingFactory.edge(
                        input,
                        config.rule(),
                        config.severity(),
                        dependency,
                        violation,
                        "Layer " + config.layers().get(sourceLayer).name() + " must not depend on layer "
                                + config.layers().get(targetLayer).name()));
            }
        }
        return findings.stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }

    private static int layerIndex(Component component, LayeredArchitectureConfig config) {
        String packageName = ComponentFacts.packageName(component);
        int match = -1;
        for (int index = 0; index < config.layers().size(); index++) {
            ArchitectureLayer layer = config.layers().get(index);
            if (layer.packages().stream().anyMatch(selector -> selector.matches(packageName))) {
                if (match >= 0) {
                    throw new RuleEngineException(
                            "rule.config.layer-overlap", "component package matches more than one layer");
                }
                match = index;
            }
        }
        return match;
    }
}
