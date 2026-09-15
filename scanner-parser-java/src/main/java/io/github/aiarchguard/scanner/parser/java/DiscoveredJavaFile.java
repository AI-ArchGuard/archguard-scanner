package io.github.aiarchguard.scanner.parser.java;

import java.nio.file.Path;

record DiscoveredJavaFile(Path absolutePath, String repositoryPath, String modulePath, long byteSize) {}
