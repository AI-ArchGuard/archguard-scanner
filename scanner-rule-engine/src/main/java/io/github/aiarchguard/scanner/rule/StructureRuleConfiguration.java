package io.github.aiarchguard.scanner.rule;

import io.github.aiarchguard.scanner.domain.model.RuleReference;
import io.github.aiarchguard.scanner.domain.model.Severity;

public sealed interface StructureRuleConfiguration
        permits IllegalPackageDependencyConfig,
                LayeredArchitectureConfig,
                DependencyCycleConfig,
                ControllerRepositoryAccessConfig,
                InternalModuleAccessConfig,
                ForbiddenComponentConfig,
                ComplexityThresholdConfig,
                RequiredAnnotationConfig {

    RuleReference rule();

    Severity severity();
}
