package io.github.aiarchguard.scanner.report;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialects;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ReportSchemaValidator {

    public static final String SCHEMA_RESOURCE = "schema/scan-result-0.1.0.schema.json";

    private final String schemaText;
    private final Schema schema;

    public ReportSchemaValidator() {
        this.schemaText = readSchema();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        this.schema = registry.getSchema(schemaText);
        this.schema.initializeValidators();
    }

    public void validate(byte[] json) {
        validate(new String(json, StandardCharsets.UTF_8));
    }

    public void validate(String json) {
        List<String> reasons = errors(schema.validate(
                json,
                InputFormat.JSON,
                context -> context.executionConfig(config -> config.locale(Locale.ROOT))));
        if (!reasons.isEmpty()) {
            throw new ReportValidationException(reasons);
        }
    }

    public void validateSchema() {
        SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());
        Schema metaSchema = registry.getSchema(SchemaLocation.of(Dialects.getDraft202012().getId()));
        List<String> reasons = errors(metaSchema.validate(
                schemaText,
                InputFormat.JSON,
                context -> context.executionConfig(config -> config.locale(Locale.ROOT))));
        if (!reasons.isEmpty()) {
            throw new ReportValidationException(reasons);
        }
    }

    public String schemaText() {
        return schemaText;
    }

    private static List<String> errors(List<Error> errors) {
        return errors.stream().map(Error::toString).sorted(Comparator.naturalOrder()).toList();
    }

    private static String readSchema() {
        ClassLoader classLoader = ReportSchemaValidator.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled schema: " + SCHEMA_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled schema: " + SCHEMA_RESOURCE, exception);
        }
    }
}
