package io.github.aiarchguard.scanner.rule;

public record ForbiddenPackageSelector(PackageSelector packages) implements ForbiddenSelector {

    public ForbiddenPackageSelector {
        if (packages == null) {
            throw new IllegalArgumentException("forbidden package selector must not be null");
        }
    }

    @Override
    public String canonicalValue() {
        return "package:" + packages.pattern();
    }
}
