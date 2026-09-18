package io.github.aiarchguard.scanner.cli;

public final class ArchGuardCli {

    private ArchGuardCli() {}

    public static void main(String[] arguments) {
        System.exit(new ScannerCli().run(arguments, System.out, System.err));
    }
}
