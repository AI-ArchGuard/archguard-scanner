package io.github.aiarchguard.scanner.rule;

public sealed interface ForbiddenSelector
        permits ForbiddenTypeSelector, ForbiddenPackageSelector, ForbiddenMavenCoordinateSelector {

    String canonicalValue();
}
