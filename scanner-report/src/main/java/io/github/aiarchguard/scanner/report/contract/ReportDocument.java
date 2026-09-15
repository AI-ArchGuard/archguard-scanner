package io.github.aiarchguard.scanner.report.contract;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReportDocument(
        String schemaVersion,
        ProjectDto project,
        List<ArtifactDto> artifacts,
        List<ComponentDto> components,
        List<DependencyDto> dependencies,
        List<MetricDto> metrics,
        List<FindingDto> findings,
        List<EvidenceDto> evidences) {

    public record ProjectDto(
            String id,
            String identity,
            String name,
            String language,
            String repositoryPath,
            Map<String, List<String>> extensions) {}

    public record ArtifactDto(
            String id,
            String projectId,
            String kind,
            String name,
            String language,
            String repositoryPath,
            String qualifiedName,
            Map<String, List<String>> extensions) {}

    public record ComponentDto(
            String id,
            String artifactId,
            String kind,
            String name,
            String language,
            String qualifiedName,
            SourceLocationDto location,
            Map<String, List<String>> extensions) {}

    public record DependencyDto(
            String id,
            String sourceId,
            String targetId,
            String kind,
            List<String> evidenceIds,
            Map<String, List<String>> extensions) {}

    public record MetricDto(
            String id,
            String scopeId,
            String key,
            BigDecimal value,
            String unit,
            Map<String, List<String>> extensions) {}

    public record FindingDto(
            String id,
            String fingerprint,
            RuleReferenceDto rule,
            String severity,
            String subjectId,
            String message,
            SourceLocationDto location,
            List<String> evidenceIds,
            Map<String, List<String>> extensions) {}

    public record EvidenceDto(
            String id,
            String kind,
            String summary,
            SourceLocationDto location,
            Map<String, List<String>> extensions) {}

    public record SourceLocationDto(String path, int startLine, int startColumn, int endLine, int endColumn) {}

    public record RuleReferenceDto(String id, String version) {}
}
