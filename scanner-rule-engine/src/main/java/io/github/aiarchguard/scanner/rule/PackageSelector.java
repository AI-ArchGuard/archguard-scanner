package io.github.aiarchguard.scanner.rule;

import java.text.Normalizer;

public record PackageSelector(String pattern) implements Comparable<PackageSelector> {

    public PackageSelector {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("package selector must not be blank");
        }
        pattern = Normalizer.normalize(pattern, Normalizer.Form.NFC);
        if (!"<default>".equals(pattern) && !isPackagePattern(pattern)) {
            throw new IllegalArgumentException(
                    "package selector must be exact, end in .* or end in .**");
        }
    }

    private static boolean isPackagePattern(String value) {
        String packageName = value.endsWith(".**")
                ? value.substring(0, value.length() - 3)
                : value.endsWith(".*") ? value.substring(0, value.length() - 2) : value;
        if (packageName.isEmpty()) {
            return false;
        }
        for (String segment : packageName.split("\\.", -1)) {
            if (!isJavaIdentifier(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJavaIdentifier(String value) {
        if (value.isEmpty()) {
            return false;
        }
        int first = value.codePointAt(0);
        if (!Character.isJavaIdentifierStart(first)) {
            return false;
        }
        for (int offset = Character.charCount(first); offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isJavaIdentifierPart(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    public boolean matches(String packageName) {
        if (packageName == null) {
            return false;
        }
        if (pattern.endsWith(".**")) {
            String base = pattern.substring(0, pattern.length() - 3);
            return packageName.equals(base) || packageName.startsWith(base + ".");
        }
        if (pattern.endsWith(".*")) {
            String base = pattern.substring(0, pattern.length() - 2);
            if (!packageName.startsWith(base + ".")) {
                return false;
            }
            return packageName.indexOf('.', base.length() + 1) < 0;
        }
        return pattern.equals(packageName);
    }

    @Override
    public int compareTo(PackageSelector other) {
        return pattern.compareTo(other.pattern);
    }
}
