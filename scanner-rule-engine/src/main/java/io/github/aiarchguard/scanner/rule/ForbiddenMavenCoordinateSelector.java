package io.github.aiarchguard.scanner.rule;

import java.util.regex.Pattern;

public record ForbiddenMavenCoordinateSelector(String coordinate) implements ForbiddenSelector {

    private static final Pattern COORDINATE = Pattern.compile("^[^:\\s]+:[^:\\s]+(?::[^:\\s]+)?$");

    public ForbiddenMavenCoordinateSelector {
        if (coordinate == null || !COORDINATE.matcher(coordinate.trim()).matches()) {
            throw new IllegalArgumentException("Maven coordinate must be G:A or G:A:V");
        }
        coordinate = coordinate.trim();
    }

    @Override
    public String canonicalValue() {
        return "maven:" + coordinate;
    }
}
