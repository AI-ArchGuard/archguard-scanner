package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.EntityKind;
import io.github.aiarchguard.scanner.domain.model.Evidence;
import io.github.aiarchguard.scanner.domain.model.Finding;
import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;
import io.github.aiarchguard.scanner.domain.model.SourceLocation;
import io.github.aiarchguard.scanner.domain.model.StableIdGenerator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class FindingFactory {

    private FindingFactory() {}

    static Finding edge(
            RuleInput input,
            RuleReference rule,
            Severity severity,
            Dependency dependency,
            String violation,
            String message) {
        return create(
                input,
                rule,
                severity,
                dependency.id(),
                dependency.evidenceIds(),
                violation,
                List.of(
                        dependency.sourceId(),
                        dependency.kind().wireValue(),
                        dependency.targetId(),
                        dependency.id()),
                message,
                Map.of("archguard.violation", List.of(violation)));
    }

    static Finding cycle(
            RuleInput input,
            RuleReference rule,
            Severity severity,
            CycleScope scope,
            List<String> nodeIds,
            List<Dependency> dependencies,
            String message) {
        List<Dependency> sortedDependencies = dependencies.stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
        TreeSet<String> evidenceIds = new TreeSet<>();
        sortedDependencies.forEach(dependency -> evidenceIds.addAll(dependency.evidenceIds()));
        return create(
                input,
                rule,
                severity,
                sortedDependencies.getFirst().id(),
                evidenceIds,
                "cycle-" + scope.wireValue(),
                List.of(
                        String.join("|", nodeIds.stream().sorted().toList()),
                        String.join("|", sortedDependencies.stream().map(Dependency::id).toList())),
                message,
                Map.of("archguard.graph-scope", List.of(scope.wireValue())));
    }

    static Finding custom(
            RuleInput input,
            RuleReference rule,
            Severity severity,
            String subjectId,
            Collection<String> evidenceIds,
            String violation,
            List<String> identityParts,
            String message,
            Map<String, List<String>> extensions) {
        return create(
                input,
                rule,
                severity,
                subjectId,
                evidenceIds,
                violation,
                identityParts,
                message,
                extensions);
    }

    private static Finding create(
            RuleInput input,
            RuleReference rule,
            Severity severity,
            String subjectId,
            Collection<String> evidenceIds,
            String violation,
            List<String> identityParts,
            String message,
            Map<String, List<String>> extensions) {
        List<String> sortedEvidenceIds = evidenceIds.stream().distinct().sorted().toList();
        Evidence primaryEvidence = input.evidenceIndex().get(sortedEvidenceIds.getFirst());
        SourceLocation location = primaryEvidence.location();
        String fingerprint = StableIdGenerator.fingerprint(
                rule.id(),
                rule.version(),
                input.project().identity(),
                violation,
                String.join("|", identityParts));
        String id = StableIdGenerator.generate(
                input.schemaVersion(),
                input.project().identity(),
                EntityKind.FINDING,
                input.project().language(),
                location.path(),
                rule.id() + "|" + fingerprint);
        return new Finding(
                id,
                fingerprint,
                rule,
                severity,
                subjectId,
                message,
                location,
                sortedEvidenceIds,
                extensions);
    }
}
