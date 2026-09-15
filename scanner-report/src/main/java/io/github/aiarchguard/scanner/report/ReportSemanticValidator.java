package io.github.aiarchguard.scanner.report;

import io.github.aiarchguard.scanner.report.contract.ReportDocument;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.DependencyDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.FindingDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.MetricDto;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class ReportSemanticValidator {

    void validate(ReportDocument document) {
        List<String> reasons = new ArrayList<>();
        Set<String> allIds = new HashSet<>();
        addUnique(allIds, document.project().id(), "$.project.id", reasons);

        Set<String> artifactIds = ids(document.artifacts(), ReportDocument.ArtifactDto::id, "$.artifacts", allIds, reasons);
        Set<String> componentIds = ids(
                document.components(), ReportDocument.ComponentDto::id, "$.components", allIds, reasons);
        Set<String> dependencyIds = ids(
                document.dependencies(), ReportDocument.DependencyDto::id, "$.dependencies", allIds, reasons);
        ids(document.metrics(), ReportDocument.MetricDto::id, "$.metrics", allIds, reasons);
        Set<String> findingIds = ids(document.findings(), ReportDocument.FindingDto::id, "$.findings", allIds, reasons);
        Set<String> evidenceIds = ids(
                document.evidences(), ReportDocument.EvidenceDto::id, "$.evidences", allIds, reasons);

        for (int index = 0; index < document.artifacts().size(); index++) {
            if (!document.project().id().equals(document.artifacts().get(index).projectId())) {
                reasons.add("$.artifacts[" + index + "].projectId must reference $.project.id");
            }
        }
        for (int index = 0; index < document.components().size(); index++) {
            if (!artifactIds.contains(document.components().get(index).artifactId())) {
                reasons.add("$.components[" + index + "].artifactId references an unknown artifact");
            }
        }
        for (int index = 0; index < document.dependencies().size(); index++) {
            DependencyDto dependency = document.dependencies().get(index);
            requireReference(componentIds, dependency.sourceId(), "$.dependencies[" + index + "].sourceId", reasons);
            requireReference(componentIds, dependency.targetId(), "$.dependencies[" + index + "].targetId", reasons);
            requireReferences(evidenceIds, dependency.evidenceIds(), "$.dependencies[" + index + "].evidenceIds", reasons);
        }

        Set<String> metricScopes = new HashSet<>();
        metricScopes.add(document.project().id());
        metricScopes.addAll(artifactIds);
        metricScopes.addAll(componentIds);
        metricScopes.addAll(dependencyIds);
        metricScopes.addAll(findingIds);
        for (int index = 0; index < document.metrics().size(); index++) {
            MetricDto metric = document.metrics().get(index);
            requireReference(metricScopes, metric.scopeId(), "$.metrics[" + index + "].scopeId", reasons);
        }

        Set<String> findingSubjects = new HashSet<>();
        findingSubjects.add(document.project().id());
        findingSubjects.addAll(artifactIds);
        findingSubjects.addAll(componentIds);
        findingSubjects.addAll(dependencyIds);
        for (int index = 0; index < document.findings().size(); index++) {
            FindingDto finding = document.findings().get(index);
            requireReference(findingSubjects, finding.subjectId(), "$.findings[" + index + "].subjectId", reasons);
            requireReferences(evidenceIds, finding.evidenceIds(), "$.findings[" + index + "].evidenceIds", reasons);
        }

        if (!reasons.isEmpty()) {
            throw new ReportValidationException(reasons.stream().sorted().toList());
        }
    }

    private static <T> Set<String> ids(
            List<T> values,
            Function<T, String> idFunction,
            String path,
            Set<String> allIds,
            List<String> reasons) {
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String id = idFunction.apply(values.get(index));
            if (!ids.add(id)) {
                reasons.add(path + "[" + index + "].id duplicates " + id);
            }
            addUnique(allIds, id, path + "[" + index + "].id", reasons);
        }
        return ids;
    }

    private static void addUnique(Set<String> allIds, String id, String path, List<String> reasons) {
        if (!allIds.add(id)) {
            reasons.add(path + " duplicates an identifier used by another entity");
        }
    }

    private static void requireReference(Set<String> ids, String id, String path, List<String> reasons) {
        if (!ids.contains(id)) {
            reasons.add(path + " references an unknown entity: " + id);
        }
    }

    private static void requireReferences(Set<String> ids, List<String> references, String path, List<String> reasons) {
        for (int index = 0; index < references.size(); index++) {
            requireReference(ids, references.get(index), path + "[" + index + "]", reasons);
        }
    }
}
