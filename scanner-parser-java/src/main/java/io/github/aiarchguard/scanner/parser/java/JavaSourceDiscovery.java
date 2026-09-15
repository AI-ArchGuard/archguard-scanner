package io.github.aiarchguard.scanner.parser.java;

import io.github.aiarchguard.scanner.domain.model.RepositoryPath;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class JavaSourceDiscovery {

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(".git", ".gradle", "build", "out", "target");

    DiscoveryResult discover(JavaScanRequest request) {
        Path requestedRoot = request.repositoryRoot();
        if (Files.isSymbolicLink(requestedRoot)) {
            throw new JavaScanException("java.path.root-symlink", "repository root must not be a symbolic link");
        }

        Path root = requestedRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new JavaScanException("java.path.invalid-root", "repository root must be a readable directory");
        }

        try {
            Path realRoot = root.toRealPath();
            List<DiscoveredJavaFile> files = new ArrayList<>();
            List<JavaParseDiagnostic> diagnostics = new ArrayList<>();
            long[] totalBytes = {0L};

            Files.walkFileTree(
                    root,
                    Set.of(),
                    request.limits().maxDepth() + 1,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                                throws IOException {
                            Path relative = root.relativize(directory);
                            if (relative.getNameCount() > request.limits().maxDepth()) {
                                throw new JavaScanException(
                                        "java.limit.depth", "repository path depth exceeds the configured limit");
                            }
                            if (!relative.toString().isEmpty()
                                    && SKIPPED_DIRECTORIES.contains(directory.getFileName().toString())
                                    && !isInsideJavaSourceTree(root, directory)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (Files.isSymbolicLink(directory)) {
                                diagnostics.add(diagnostic(
                                        "java.path.symlink", repositoryPath(root, directory), "symbolic link was skipped"));
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            Path realDirectory = directory.toRealPath();
                            if (!realDirectory.startsWith(realRoot)) {
                                throw new JavaScanException(
                                        "java.path.escape", "repository path resolves outside the repository root");
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                            if (root.relativize(file).getNameCount() > request.limits().maxDepth()) {
                                throw new JavaScanException(
                                        "java.limit.depth", "repository path depth exceeds the configured limit");
                            }
                            String relativePath = repositoryPath(root, file);
                            if (Files.isSymbolicLink(file)) {
                                if (isJavaSourcePath(relativePath)) {
                                    diagnostics.add(diagnostic(
                                            "java.path.symlink", relativePath, "symbolic link was skipped"));
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            String modulePath = modulePath(root, file, relativePath);
                            if (modulePath == null) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (!attributes.isRegularFile()) {
                                diagnostics.add(diagnostic(
                                        "java.path.special-file", relativePath, "non-regular Java source was skipped"));
                                return FileVisitResult.CONTINUE;
                            }

                            long size = attributes.size();
                            if (size > request.limits().maxFileBytes()) {
                                throw new JavaScanException(
                                        "java.limit.file-bytes", "Java source exceeds the configured per-file limit");
                            }
                            if (files.size() >= request.limits().maxFiles()) {
                                throw new JavaScanException(
                                        "java.limit.files", "Java source count exceeds the configured limit");
                            }
                            if (totalBytes[0] > request.limits().maxTotalBytes() - size) {
                                throw new JavaScanException(
                                        "java.limit.total-bytes", "Java source bytes exceed the configured limit");
                            }
                            totalBytes[0] += size;
                            files.add(new DiscoveredJavaFile(file, relativePath, modulePath, size));
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException failure) {
                            diagnostics.add(diagnostic(
                                    "java.path.unreadable", repositoryPath(root, file), "repository entry was unreadable"));
                            return FileVisitResult.CONTINUE;
                        }
                    });

            files.sort((left, right) -> left.repositoryPath().compareTo(right.repositoryPath()));
            diagnostics.sort(null);
            return new DiscoveryResult(List.copyOf(files), List.copyOf(diagnostics), root, realRoot);
        } catch (JavaScanException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new JavaScanException(
                    "java.discovery.failed", "Java source discovery failed without exposing host paths");
        }
    }

    private static String modulePath(Path root, Path file, String repositoryPath) {
        if (!repositoryPath.endsWith(".java")) {
            return null;
        }
        Path relative = root.relativize(file);
        for (int index = 0; index + 3 < relative.getNameCount(); index++) {
            if ("src".equals(relative.getName(index).toString())
                    && "main".equals(relative.getName(index + 1).toString())
                    && "java".equals(relative.getName(index + 2).toString())) {
                Path moduleRoot = index == 0 ? root : root.resolve(relative.subpath(0, index));
                if (!Files.isRegularFile(moduleRoot.resolve("pom.xml"), LinkOption.NOFOLLOW_LINKS)) {
                    return null;
                }
                return index == 0 ? "." : RepositoryPath.normalize(relative.subpath(0, index).toString());
            }
        }
        return null;
    }

    private static boolean isJavaSourcePath(String repositoryPath) {
        return repositoryPath.endsWith(".java")
                && (repositoryPath.startsWith("src/main/java/")
                        || repositoryPath.contains("/src/main/java/"));
    }

    private static boolean isInsideJavaSourceTree(Path root, Path directory) {
        Path relative = root.relativize(directory);
        for (int index = 0; index + 2 < relative.getNameCount(); index++) {
            if ("src".equals(relative.getName(index).toString())
                    && "main".equals(relative.getName(index + 1).toString())
                    && "java".equals(relative.getName(index + 2).toString())) {
                Path moduleRoot = index == 0 ? root : root.resolve(relative.subpath(0, index));
                return Files.isRegularFile(moduleRoot.resolve("pom.xml"), LinkOption.NOFOLLOW_LINKS);
            }
        }
        return false;
    }

    private static String repositoryPath(Path root, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new JavaScanException("java.path.escape", "repository path is outside the repository root");
        }
        String relative = root.relativize(normalized).toString();
        return relative.isEmpty() ? "." : RepositoryPath.normalize(relative);
    }

    private static JavaParseDiagnostic diagnostic(String code, String path, String message) {
        return new JavaParseDiagnostic(code, path, 1, 1, message);
    }

    record DiscoveryResult(
            List<DiscoveredJavaFile> files,
            List<JavaParseDiagnostic> diagnostics,
            Path absoluteRoot,
            Path realRoot) {}
}
