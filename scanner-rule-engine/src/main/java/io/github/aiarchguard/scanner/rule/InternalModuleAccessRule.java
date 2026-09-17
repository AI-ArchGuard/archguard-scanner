package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InternalModuleAccessRule {

    Evaluation evaluate(RuleInput input, InternalModuleAccessConfig config, int maxFindings) {
        Map<String, Component> components = input.componentIndex();
        Map<String, Artifact> artifacts = input.artifactIndex();
        Map<String, ModuleBoundary> boundaries = new LinkedHashMap<>();
        config.boundaries().forEach(boundary -> boundaries.put(boundary.modulePath(), boundary));
        List<Finding> findings = new ArrayList<>();
        List<RuleDiagnostic> diagnostics = new ArrayList<>();
        for (Dependency dependency : input.dependencies()) {
            Component source = components.get(dependency.sourceId());
            Component target = components.get(dependency.targetId());
            if (source.artifactId().equals(target.artifactId())) {
                continue;
            }
            var targetPackage = ComponentFacts.optionalPackageName(target);
            if (targetPackage.isEmpty()) {
                continue;
            }
            Artifact targetArtifact = artifacts.get(target.artifactId());
            ModuleBoundary boundary = boundaries.get(targetArtifact.repositoryPath());
            if (boundary == null) {
                diagnostics.add(new RuleDiagnostic(
                        "rule.module-boundary.missing",
                        targetArtifact.id(),
                        "target Maven module has no public API boundary configuration"));
                continue;
            }
            if (boundary.publicPackages().stream().anyMatch(selector -> selector.matches(targetPackage.orElseThrow()))) {
                continue;
            }
            if (findings.size() >= maxFindings) {
                throw new RuleEngineException("rule.limit.findings", "rule finding limit exceeded");
            }
            findings.add(FindingFactory.edge(
                    input,
                    config.rule(),
                    config.severity(),
                    dependency,
                    "internal-module-access",
                    "Cross-module dependency targets internal package " + targetPackage.orElseThrow()));
        }
        return new Evaluation(findings, diagnostics);
    }

    record Evaluation(List<Finding> findings, List<RuleDiagnostic> diagnostics) {}
}
