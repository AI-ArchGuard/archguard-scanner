package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.Finding;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ForbiddenComponentRule {

    Evaluation evaluate(RuleInput input, ForbiddenComponentConfig config, int maxFindings) {
        Map<String, Component> components = input.componentIndex();
        Map<String, Artifact> artifacts = input.artifactIndex();
        List<Finding> findings = new ArrayList<>();
        List<RuleDiagnostic> diagnostics = new ArrayList<>();
        for (Dependency dependency : input.dependencies()) {
            Component target = components.get(dependency.targetId());
            Artifact targetArtifact = artifacts.get(target.artifactId());
            ForbiddenSelector matched = config.selectors().stream()
                    .filter(selector -> matches(selector, target, targetArtifact))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                if (config.selectors().stream().anyMatch(ForbiddenMavenCoordinateSelector.class::isInstance)
                        && targetArtifact.extensions().containsKey("maven.coordinate-unresolved")) {
                    diagnostics.add(new RuleDiagnostic(
                            "rule.maven-coordinate.unresolved",
                            targetArtifact.id(),
                            "target Maven coordinate could not be resolved deterministically"));
                } else if (targetArtifact.extensions().containsKey("maven.version-unresolved")
                        && config.selectors().stream()
                                .filter(ForbiddenMavenCoordinateSelector.class::isInstance)
                                .map(ForbiddenMavenCoordinateSelector.class::cast)
                                .anyMatch(selector -> selector.coordinate().chars().filter(ch -> ch == ':').count() == 2)) {
                    diagnostics.add(new RuleDiagnostic(
                            "rule.maven-version.unresolved",
                            targetArtifact.id(),
                            "target Maven version could not be resolved for a version-specific selector"));
                }
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
                    "forbidden-component",
                    "Dependency targets forbidden component selector " + matched.canonicalValue()));
        }
        return new Evaluation(findings, diagnostics);
    }

    private static boolean matches(ForbiddenSelector selector, Component target, Artifact artifact) {
        return switch (selector) {
            case ForbiddenTypeSelector type -> target.qualifiedName().equals(type.qualifiedName());
            case ForbiddenPackageSelector packages -> ComponentFacts.optionalPackageName(target)
                    .map(packages.packages()::matches)
                    .orElse(false);
            case ForbiddenMavenCoordinateSelector maven -> S5RuleFacts.mavenCoordinate(artifact)
                    .map(coordinate -> coordinate.equals(maven.coordinate())
                            || (maven.coordinate().chars().filter(ch -> ch == ':').count() == 1
                                    && coordinate.startsWith(maven.coordinate() + ":")))
                    .orElse(false);
        };
    }

    record Evaluation(List<Finding> findings, List<RuleDiagnostic> diagnostics) {}
}
