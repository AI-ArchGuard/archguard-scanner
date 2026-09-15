package io.github.aiarchguard.scanner.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.aiarchguard.scanner.report.contract.ReportDocument;
import java.io.IOException;
import java.util.List;

public final class ReportJson {

    private final ObjectMapper objectMapper;
    private final ReportMapper reportMapper;
    private final ReportSchemaValidator schemaValidator;
    private final ReportSemanticValidator semanticValidator;

    public ReportJson() {
        this.objectMapper = JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.reportMapper = new ReportMapper();
        this.schemaValidator = new ReportSchemaValidator();
        this.semanticValidator = new ReportSemanticValidator();
        this.schemaValidator.validateSchema();
    }

    public byte[] serialize(DomainReport report) {
        ReportDocument document = reportMapper.toContract(report);
        semanticValidator.validate(document);
        try {
            byte[] json = objectMapper.writeValueAsBytes(document);
            schemaValidator.validate(json);
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize scanner report", exception);
        }
    }

    public DomainReport deserialize(byte[] json) {
        schemaValidator.validate(json);
        try {
            ReportDocument document = objectMapper.readValue(json, ReportDocument.class);
            semanticValidator.validate(document);
            return reportMapper.toDomain(document);
        } catch (ReportValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ReportValidationException(List.of(exception.getMessage()));
        } catch (IOException exception) {
            throw new ReportValidationException(List.of("invalid JSON: " + exception.getMessage()));
        }
    }

    public void validate(byte[] json) {
        deserialize(json);
    }

    public String schema() {
        return schemaValidator.schemaText();
    }
}
