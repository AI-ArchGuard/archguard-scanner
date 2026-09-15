package io.github.aiarchguard.scanner.report;

import io.github.aiarchguard.scanner.domain.model.Artifact;
import io.github.aiarchguard.scanner.domain.model.ArtifactKind;
import io.github.aiarchguard.scanner.domain.model.Component;
import io.github.aiarchguard.scanner.domain.model.ComponentKind;
import io.github.aiarchguard.scanner.domain.model.Dependency;
import io.github.aiarchguard.scanner.domain.model.DependencyKind;
import io.github.aiarchguard.scanner.domain.model.Evidence;
import io.github.aiarchguard.scanner.domain.model.EvidenceKind;
import io.github.aiarchguard.scanner.domain.model.Finding;
import io.github.aiarchguard.scanner.domain.model.Metric;
import io.github.aiarchguard.scanner.domain.model.Project;
import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;
import io.github.aiarchguard.scanner.domain.model.SourceLocation;
import io.github.aiarchguard.scanner.report.contract.ReportDocument;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.ArtifactDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.ComponentDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.DependencyDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.EvidenceDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.FindingDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.MetricDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.ProjectDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.RuleReferenceDto;
import io.github.aiarchguard.scanner.report.contract.ReportDocument.SourceLocationDto;
import java.util.Comparator;
import java.util.List;

public final class ReportMapper {

    public static final String SCHEMA_VERSION = "0.1.0";

    public ReportDocument toContract(DomainReport report) {
        Project project = report.project();
        ProjectDto projectDto = new ProjectDto(
                project.id(),
                project.identity(),
                project.name(),
                project.language(),
                project.repositoryPath(),
                project.extensions());

        List<ArtifactDto> artifacts = report.artifacts().stream()
                .sorted(Comparator.comparing(Artifact::id))
                .map(value -> new ArtifactDto(
                        value.id(),
                        value.projectId(),
                        value.kind().wireValue(),
                        value.name(),
                        value.language(),
                        value.repositoryPath(),
                        value.qualifiedName(),
                        value.extensions()))
                .toList();
        List<ComponentDto> components = report.components().stream()
                .sorted(Comparator.comparing(Component::id))
                .map(value -> new ComponentDto(
                        value.id(),
                        value.artifactId(),
                        value.kind().wireValue(),
                        value.name(),
                        value.language(),
                        value.qualifiedName(),
                        toDto(value.location()),
                        value.extensions()))
                .toList();
        List<DependencyDto> dependencies = report.dependencies().stream()
                .sorted(Comparator.comparing(Dependency::id))
                .map(value -> new DependencyDto(
                        value.id(),
                        value.sourceId(),
                        value.targetId(),
                        value.kind().wireValue(),
                        value.evidenceIds(),
                        value.extensions()))
                .toList();
        List<MetricDto> metrics = report.metrics().stream()
                .sorted(Comparator.comparing(Metric::id))
                .map(value -> new MetricDto(
                        value.id(), value.scopeId(), value.key(), value.value(), value.unit(), value.extensions()))
                .toList();
        List<FindingDto> findings = report.findings().stream()
                .sorted(Comparator.comparing(Finding::id))
                .map(value -> new FindingDto(
                        value.id(),
                        value.fingerprint(),
                        new RuleReferenceDto(value.rule().id(), value.rule().version()),
                        value.severity().wireValue(),
                        value.subjectId(),
                        value.message(),
                        toDto(value.location()),
                        value.evidenceIds(),
                        value.extensions()))
                .toList();
        List<EvidenceDto> evidences = report.evidences().stream()
                .sorted(Comparator.comparing(Evidence::id))
                .map(value -> new EvidenceDto(
                        value.id(),
                        value.kind().wireValue(),
                        value.summary(),
                        toDto(value.location()),
                        value.extensions()))
                .toList();

        return new ReportDocument(
                SCHEMA_VERSION, projectDto, artifacts, components, dependencies, metrics, findings, evidences);
    }

    public DomainReport toDomain(ReportDocument document) {
        ProjectDto project = document.project();
        return new DomainReport(
                new Project(
                        project.id(),
                        project.identity(),
                        project.name(),
                        project.language(),
                        project.repositoryPath(),
                        project.extensions()),
                document.artifacts().stream().map(this::toDomain).toList(),
                document.components().stream().map(this::toDomain).toList(),
                document.dependencies().stream().map(this::toDomain).toList(),
                document.metrics().stream().map(this::toDomain).toList(),
                document.findings().stream().map(this::toDomain).toList(),
                document.evidences().stream().map(this::toDomain).toList());
    }

    private Artifact toDomain(ArtifactDto value) {
        return new Artifact(
                value.id(),
                value.projectId(),
                ArtifactKind.fromWireValue(value.kind()),
                value.name(),
                value.language(),
                value.repositoryPath(),
                value.qualifiedName(),
                value.extensions());
    }

    private Component toDomain(ComponentDto value) {
        return new Component(
                value.id(),
                value.artifactId(),
                ComponentKind.fromWireValue(value.kind()),
                value.name(),
                value.language(),
                value.qualifiedName(),
                toDomain(value.location()),
                value.extensions());
    }

    private Dependency toDomain(DependencyDto value) {
        return new Dependency(
                value.id(),
                value.sourceId(),
                value.targetId(),
                DependencyKind.fromWireValue(value.kind()),
                value.evidenceIds(),
                value.extensions());
    }

    private Metric toDomain(MetricDto value) {
        return new Metric(value.id(), value.scopeId(), value.key(), value.value(), value.unit(), value.extensions());
    }

    private Finding toDomain(FindingDto value) {
        return new Finding(
                value.id(),
                value.fingerprint(),
                new RuleReference(value.rule().id(), value.rule().version()),
                Severity.fromWireValue(value.severity()),
                value.subjectId(),
                value.message(),
                toDomain(value.location()),
                value.evidenceIds(),
                value.extensions());
    }

    private Evidence toDomain(EvidenceDto value) {
        return new Evidence(
                value.id(),
                EvidenceKind.fromWireValue(value.kind()),
                value.summary(),
                toDomain(value.location()),
                value.extensions());
    }

    private static SourceLocationDto toDto(SourceLocation value) {
        return value == null
                ? null
                : new SourceLocationDto(
                        value.path(), value.startLine(), value.startColumn(), value.endLine(), value.endColumn());
    }

    private static SourceLocation toDomain(SourceLocationDto value) {
        return value == null
                ? null
                : new SourceLocation(
                        value.path(), value.startLine(), value.startColumn(), value.endLine(), value.endColumn());
    }
}
