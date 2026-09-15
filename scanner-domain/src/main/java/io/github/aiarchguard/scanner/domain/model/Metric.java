package io.github.aiarchguard.scanner.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record Metric(
        String id,
        String scopeId,
        String key,
        BigDecimal value,
        String unit,
        Map<String, List<String>> extensions) {

    public Metric {
        id = ModelValidation.requireId(id, EntityKind.METRIC);
        scopeId = ModelValidation.requireText(scopeId, "metric scopeId");
        key = ModelValidation.requireText(key, "metric key");
        value = ModelValidation.decimal(value);
        unit = ModelValidation.requireText(unit, "metric unit");
        extensions = ModelValidation.extensions(extensions);
    }
}
