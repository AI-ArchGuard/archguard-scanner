package io.github.aiarchguard.scanner.cli;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

record CliArguments(Path repository, Path rules, Path output) {

    static CliArguments parse(String[] arguments) {
        if (arguments == null || arguments.length == 0 || !"scan".equals(arguments[0])) {
            throw new CliUsageException("expected the scan command");
        }
        Path repository = null;
        Map<String, Path> options = new HashMap<>();
        for (int index = 1; index < arguments.length; index++) {
            String argument = arguments[index];
            if (argument.startsWith("--")) {
                if (!"--rules".equals(argument) && !"--output".equals(argument)) {
                    throw new CliUsageException("unknown option: " + argument);
                }
                if (index + 1 >= arguments.length || arguments[index + 1].startsWith("--")) {
                    throw new CliUsageException("missing value for " + argument);
                }
                if (options.putIfAbsent(argument, Path.of(arguments[++index])) != null) {
                    throw new CliUsageException("duplicate option: " + argument);
                }
            } else if (repository == null) {
                repository = Path.of(argument);
            } else {
                throw new CliUsageException("scan accepts exactly one repository path");
            }
        }
        if (repository == null || !options.containsKey("--rules") || !options.containsKey("--output")) {
            throw new CliUsageException("repository, --rules, and --output are required");
        }
        Path rules = options.get("--rules").toAbsolutePath().normalize();
        Path output = options.get("--output").toAbsolutePath().normalize();
        if (rules.equals(output)) {
            throw new CliUsageException("rules and output must be different files");
        }
        return new CliArguments(repository, rules, output);
    }
}
