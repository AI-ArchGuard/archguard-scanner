package io.github.aiarchguard.scanner.parser.java;

import java.nio.file.Path;

public record JavaScanRequest(
        Path repositoryRoot,
        String projectIdentity,
        String projectName,
        String schemaVersion,
        JavaScanLimits limits) {

    public JavaScanRequest {
        if (repositoryRoot == null) {
            throw new IllegalArgumentException("repositoryRoot must not be null");
        }
        projectIdentity = JavaParserValidation.requireText(projectIdentity, "projectIdentity");
        projectName = JavaParserValidation.requireText(projectName, "projectName");
        schemaVersion = JavaParserValidation.requireText(schemaVersion, "schemaVersion");
        if (!"0.1.0".equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be 0.1.0");
        }
        if (limits == null) {
            throw new IllegalArgumentException("limits must not be null");
        }
    }

    public static JavaScanRequest forSchema010(
            Path repositoryRoot, String projectIdentity, String projectName) {
        return new JavaScanRequest(
                repositoryRoot, projectIdentity, projectName, "0.1.0", JavaScanLimits.defaults());
    }
}
