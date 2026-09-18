package io.github.aiarchguard.scanner.cli;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialects;
import io.github.aiarchguard.scanner.domain.model.ComponentKind;
import io.github.aiarchguard.scanner.domain.model.Severity;
import io.github.aiarchguard.scanner.parser.java.JavaScanLimits;
import io.github.aiarchguard.scanner.rule.AnnotationRequirement;
import io.github.aiarchguard.scanner.rule.ArchitectureLayer;
import io.github.aiarchguard.scanner.rule.ComplexityThresholdConfig;
import io.github.aiarchguard.scanner.rule.ControllerRepositoryAccessConfig;
import io.github.aiarchguard.scanner.rule.CycleScope;
import io.github.aiarchguard.scanner.rule.DependencyCycleConfig;
import io.github.aiarchguard.scanner.rule.ForbiddenComponentConfig;
import io.github.aiarchguard.scanner.rule.ForbiddenMavenCoordinateSelector;
import io.github.aiarchguard.scanner.rule.ForbiddenPackageSelector;
import io.github.aiarchguard.scanner.rule.ForbiddenSelector;
import io.github.aiarchguard.scanner.rule.ForbiddenTypeSelector;
import io.github.aiarchguard.scanner.rule.IllegalPackageDependencyConfig;
import io.github.aiarchguard.scanner.rule.InternalModuleAccessConfig;
import io.github.aiarchguard.scanner.rule.LayeredArchitectureConfig;
import io.github.aiarchguard.scanner.rule.ModuleBoundary;
import io.github.aiarchguard.scanner.rule.PackagePolicy;
import io.github.aiarchguard.scanner.rule.PackageSelector;
import io.github.aiarchguard.scanner.rule.RequiredAnnotationConfig;
import io.github.aiarchguard.scanner.rule.RuleExecutionLimits;
import io.github.aiarchguard.scanner.rule.StructureRuleConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class RuleConfigurationLoader {

    static final String SCHEMA_RESOURCE = "schema/rules-0.1.0.schema.json";
    static final long MAX_CONFIGURATION_BYTES = 1024L * 1024;

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final String schemaText;
    private final Schema schema;

    RuleConfigurationLoader() {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(64)
                .maxStringLength(16_384)
                .maxNumberLength(32)
                .build());
        this.yamlMapper = new ObjectMapper(yamlFactory)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.schemaText = readSchema();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        this.schema = registry.getSchema(schemaText);
        this.schema.initializeValidators();
        validateSchema();
    }

    ScannerConfiguration load(Path path) {
        if (path == null || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ConfigurationException("config.path.invalid", "rules file must be a regular non-symbolic file");
        }
        try {
            long size = Files.size(path);
            if (size < 1 || size > MAX_CONFIGURATION_BYTES) {
                throw new ConfigurationException(
                        "config.limit.bytes", "rules file must be between 1 and 1048576 bytes");
            }
            byte[] yaml = Files.readAllBytes(path);
            if (yaml.length > MAX_CONFIGURATION_BYTES) {
                throw new ConfigurationException(
                        "config.limit.bytes", "rules file must be between 1 and 1048576 bytes");
            }
            String yamlText = decodeUtf8(yaml);
            rejectUnsafeYamlTokens(yamlText);
            JsonNode root = yamlMapper.readTree(yamlText);
            if (root == null) {
                throw new ConfigurationException("config.yaml.empty", "rules file must contain one YAML document");
            }
            validate(root);
            return map(root);
        } catch (ConfigurationException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new ConfigurationException("config.yaml.invalid", "rules file is not valid single-document YAML");
        } catch (IOException exception) {
            throw new ConfigurationException("config.read.failed", "rules file could not be read");
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException("config.value.invalid", safeMessage(exception));
        }
    }

    String schemaText() {
        return schemaText;
    }

    private ScannerConfiguration map(JsonNode root) {
        JsonNode project = root.get("project");
        JsonNode limits = root.path("limits");
        JavaScanLimits scanLimits = new JavaScanLimits(
                intValue(limits, "maxFiles", JavaScanLimits.DEFAULT_MAX_FILES),
                longValue(limits, "maxFileBytes", JavaScanLimits.DEFAULT_MAX_FILE_BYTES),
                longValue(limits, "maxTotalBytes", JavaScanLimits.DEFAULT_MAX_TOTAL_BYTES),
                intValue(limits, "maxDepth", JavaScanLimits.DEFAULT_MAX_DEPTH));
        RuleExecutionLimits ruleLimits = new RuleExecutionLimits(
                intValue(limits, "maxNodes", RuleExecutionLimits.DEFAULT_MAX_NODES),
                intValue(limits, "maxEdges", RuleExecutionLimits.DEFAULT_MAX_EDGES),
                intValue(limits, "maxFindings", RuleExecutionLimits.DEFAULT_MAX_FINDINGS));
        List<StructureRuleConfiguration> rules = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (JsonNode rule : root.get("rules")) {
            String id = rule.get("id").textValue();
            if (!identifiers.add(id)) {
                throw new IllegalArgumentException("each rule id may only be configured once");
            }
            rules.add(mapRule(id, rule));
        }
        return new ScannerConfiguration(
                project.get("identity").textValue(),
                project.get("name").textValue(),
                severity(root, "failOn", Severity.HIGH),
                scanLimits,
                ruleLimits,
                longValue(limits, "maxOutputBytes", ScannerConfiguration.DEFAULT_MAX_OUTPUT_BYTES),
                rules);
    }

    private StructureRuleConfiguration mapRule(String id, JsonNode rule) {
        JsonNode parameters = rule.path("parameters");
        return switch (id) {
            case "archguard.illegal-package-dependency" -> new IllegalPackageDependencyConfig(
                    mapPackagePolicies(parameters.get("policies")), severity(rule, "severity", Severity.HIGH));
            case "archguard.layered-architecture" -> new LayeredArchitectureConfig(
                    mapLayers(parameters.get("layers")),
                    booleanValue(parameters, "allowLayerSkipping", false),
                    severity(rule, "severity", Severity.HIGH));
            case "archguard.dependency-cycle" -> new DependencyCycleConfig(
                    CycleScope.valueOf(parameters.get("scope").textValue().toUpperCase(Locale.ROOT)),
                    severity(rule, "severity", Severity.HIGH));
            case "spring.controller-repository-access" ->
                new ControllerRepositoryAccessConfig(severity(rule, "severity", Severity.HIGH));
            case "archguard.internal-module-access" -> new InternalModuleAccessConfig(
                    mapBoundaries(parameters.get("boundaries")), severity(rule, "severity", Severity.HIGH));
            case "archguard.forbidden-component" -> new ForbiddenComponentConfig(
                    mapForbiddenSelectors(parameters.get("selectors")), severity(rule, "severity", Severity.HIGH));
            case "archguard.complexity-threshold" -> new ComplexityThresholdConfig(
                    nullableInt(parameters, "functionThreshold"),
                    nullableInt(parameters, "typeThreshold"),
                    nullableInt(parameters, "moduleThreshold"),
                    severity(rule, "severity", Severity.MEDIUM));
            case "archguard.required-annotation" -> new RequiredAnnotationConfig(
                    mapAnnotationRequirements(parameters.get("requirements")),
                    severity(rule, "severity", Severity.MEDIUM));
            default -> throw new IllegalArgumentException("unknown rule id");
        };
    }

    private static List<PackagePolicy> mapPackagePolicies(JsonNode values) {
        List<PackagePolicy> result = new ArrayList<>();
        for (JsonNode value : values) {
            result.add(new PackagePolicy(
                    new PackageSelector(value.get("source").textValue()),
                    packageSelectors(value.path("allowedTargets")),
                    packageSelectors(value.path("deniedTargets"))));
        }
        return List.copyOf(result);
    }

    private static List<ArchitectureLayer> mapLayers(JsonNode values) {
        List<ArchitectureLayer> result = new ArrayList<>();
        for (JsonNode value : values) {
            result.add(new ArchitectureLayer(value.get("name").textValue(), packageSelectors(value.get("packages"))));
        }
        return List.copyOf(result);
    }

    private static List<ModuleBoundary> mapBoundaries(JsonNode values) {
        List<ModuleBoundary> result = new ArrayList<>();
        for (JsonNode value : values) {
            result.add(new ModuleBoundary(
                    value.get("modulePath").textValue(), packageSelectors(value.get("publicPackages"))));
        }
        return List.copyOf(result);
    }

    private static List<ForbiddenSelector> mapForbiddenSelectors(JsonNode values) {
        List<ForbiddenSelector> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (value.has("type")) {
                result.add(new ForbiddenTypeSelector(value.get("type").textValue()));
            } else if (value.has("package")) {
                result.add(new ForbiddenPackageSelector(new PackageSelector(value.get("package").textValue())));
            } else {
                result.add(new ForbiddenMavenCoordinateSelector(value.get("maven").textValue()));
            }
        }
        return List.copyOf(result);
    }

    private static List<AnnotationRequirement> mapAnnotationRequirements(JsonNode values) {
        List<AnnotationRequirement> result = new ArrayList<>();
        for (JsonNode value : values) {
            Set<ComponentKind> kinds = new HashSet<>();
            value.get("componentKinds").forEach(node -> kinds.add(ComponentKind.fromWireValue(node.textValue())));
            result.add(new AnnotationRequirement(
                    value.get("annotation").textValue(),
                    kinds,
                    strings(value.path("declarationKinds")),
                    packageSelectors(value.path("packages"))));
        }
        return List.copyOf(result);
    }

    private static List<PackageSelector> packageSelectors(JsonNode values) {
        List<PackageSelector> result = new ArrayList<>();
        values.forEach(value -> result.add(new PackageSelector(value.textValue())));
        return List.copyOf(result);
    }

    private static List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.textValue()));
        return List.copyOf(result);
    }

    private static Severity severity(JsonNode node, String field, Severity fallback) {
        return node.has(field) ? Severity.fromWireValue(node.get(field).textValue()) : fallback;
    }

    private static int intValue(JsonNode node, String field, int fallback) {
        return node.has(field) ? node.get(field).intValue() : fallback;
    }

    private static long longValue(JsonNode node, String field, long fallback) {
        return node.has(field) ? node.get(field).longValue() : fallback;
    }

    private static Integer nullableInt(JsonNode node, String field) {
        return node.has(field) ? node.get(field).intValue() : null;
    }

    private static boolean booleanValue(JsonNode node, String field, boolean fallback) {
        return node.has(field) ? node.get(field).booleanValue() : fallback;
    }

    private void validate(JsonNode root) throws JsonProcessingException {
        String json = jsonMapper.writeValueAsString(root);
        List<String> reasons = schema.validate(
                        json,
                        InputFormat.JSON,
                        context -> context.executionConfig(config -> config.locale(Locale.ROOT)))
                .stream()
                .map(error -> error.getInstanceLocation() + ": violates " + error.getKeyword())
                .sorted(Comparator.naturalOrder())
                .toList();
        if (!reasons.isEmpty()) {
            throw new ConfigurationException("config.schema.invalid", String.join("; ", reasons));
        }
    }

    private void validateSchema() {
        SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());
        Schema metaSchema = registry.getSchema(SchemaLocation.of(Dialects.getDraft202012().getId()));
        List<Error> errors = metaSchema.validate(schemaText, InputFormat.JSON);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("bundled rules schema is invalid");
        }
    }

    private static void rejectUnsafeYamlTokens(String yaml) {
        for (String line : yaml.split("\\R", -1)) {
            boolean singleQuoted = false;
            boolean doubleQuoted = false;
            boolean escaped = false;
            for (int index = 0; index < line.length(); index++) {
                char value = line.charAt(index);
                if (doubleQuoted) {
                    if (escaped) {
                        escaped = false;
                    } else if (value == '\\') {
                        escaped = true;
                    } else if (value == '"') {
                        doubleQuoted = false;
                    }
                    continue;
                }
                if (singleQuoted) {
                    if (value == '\'' && index + 1 < line.length() && line.charAt(index + 1) == '\'') {
                        index++;
                    } else if (value == '\'') {
                        singleQuoted = false;
                    }
                    continue;
                }
                if (value == '#') {
                    break;
                }
                if (value == '\'') {
                    singleQuoted = true;
                    continue;
                }
                if (value == '"') {
                    doubleQuoted = true;
                    continue;
                }
                boolean tokenStart = index == 0
                        || Character.isWhitespace(line.charAt(index - 1))
                        || ":-,[{".indexOf(line.charAt(index - 1)) >= 0;
                boolean hasName = index + 1 < line.length()
                        && !Character.isWhitespace(line.charAt(index + 1))
                        && ",[]{}#".indexOf(line.charAt(index + 1)) < 0;
                if (tokenStart && hasName && (value == '&' || value == '*' || value == '!')) {
                    throw new ConfigurationException(
                            "config.yaml.reference", "YAML anchors, aliases, and explicit tags are not supported");
                }
            }
        }
    }

    private static String safeMessage(IllegalArgumentException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "rules configuration contains an invalid value" : message;
    }

    private static String decodeUtf8(byte[] yaml) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(yaml))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ConfigurationException("config.encoding.invalid", "rules file must be valid UTF-8");
        }
    }

    private static String readSchema() {
        try (InputStream input = RuleConfigurationLoader.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing bundled rules schema");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read bundled rules schema", exception);
        }
    }
}
