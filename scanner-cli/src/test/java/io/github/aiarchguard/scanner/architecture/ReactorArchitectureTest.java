package io.github.aiarchguard.scanner.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

class ReactorArchitectureTest {

    private static final String ARCHGUARD_GROUP = "io.github.aiarchguard";
    private static final Map<String, List<String>> EXPECTED_INTERNAL_DEPENDENCIES = expectedDependencies();
    private static final Set<String> FORBIDDEN_GROUP_PREFIXES = Set.of(
            "org.springframework",
            "dev.langchain4j",
            "com.openai",
            "org.postgresql",
            "com.mysql",
            "org.mongodb");

    @Test
    void reactorDeclaresExactlyTheFiveApprovedModules() throws Exception {
        Document rootPom = parsePom(reactorRoot().resolve("pom.xml"));

        assertEquals(
                List.copyOf(EXPECTED_INTERNAL_DEPENDENCIES.keySet()),
                directChildTexts(firstDirectChild(rootPom.getDocumentElement(), "modules"), "module"));
    }

    @Test
    void moduleDependenciesFollowTheApprovedDirection() throws Exception {
        for (Map.Entry<String, List<String>> entry : EXPECTED_INTERNAL_DEPENDENCIES.entrySet()) {
            Document modulePom = parsePom(reactorRoot().resolve(entry.getKey()).resolve("pom.xml"));
            List<Dependency> dependencies = dependencies(modulePom);
            List<String> internalProductionDependencies = dependencies.stream()
                    .filter(Dependency::isProduction)
                    .filter(dependency -> ARCHGUARD_GROUP.equals(dependency.groupId()))
                    .map(Dependency::artifactId)
                    .toList();

            assertEquals(entry.getValue(), internalProductionDependencies, entry.getKey());
            assertFalse(
                    dependencies.stream()
                            .filter(Dependency::isProduction)
                            .anyMatch(Dependency::isForbiddenExternal),
                    () -> entry.getKey() + " has a forbidden production dependency: " + dependencies);
            assertFalse(
                    dependencies.stream()
                            .filter(Dependency::isProduction)
                            .anyMatch(dependency -> dependency.artifactId().startsWith("archguard-platform")),
                    () -> entry.getKey() + " must not depend on Platform artifacts");
        }
    }

    @Test
    void domainHasNoProductionDependencies() throws Exception {
        Document domainPom = parsePom(reactorRoot().resolve("scanner-domain").resolve("pom.xml"));

        assertTrue(
                dependencies(domainPom).stream().noneMatch(Dependency::isProduction),
                "scanner-domain must remain free of production dependencies during S1");
    }

    private static Map<String, List<String>> expectedDependencies() {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("scanner-domain", List.of());
        expected.put("scanner-parser-java", List.of("scanner-domain"));
        expected.put("scanner-rule-engine", List.of("scanner-domain"));
        expected.put("scanner-report", List.of("scanner-domain"));
        expected.put(
                "scanner-cli",
                List.of("scanner-domain", "scanner-parser-java", "scanner-rule-engine", "scanner-report"));
        return expected;
    }

    private static Path reactorRoot() throws IOException {
        String configuredRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (configuredRoot != null) {
            Path candidate = Path.of(configuredRoot).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate.resolve("scanner-domain").resolve("pom.xml"))) {
                return candidate;
            }
        }

        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("scanner-domain").resolve("pom.xml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IOException("Cannot locate the ArchGuard Scanner reactor root");
    }

    private static Document parsePom(Path path)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static List<Dependency> dependencies(Document pom) {
        Element dependencies = firstDirectChild(pom.getDocumentElement(), "dependencies");
        if (dependencies == null) {
            return List.of();
        }

        List<Dependency> result = new ArrayList<>();
        for (Element dependency : directChildren(dependencies, "dependency")) {
            result.add(new Dependency(
                    requiredText(dependency, "groupId"),
                    requiredText(dependency, "artifactId"),
                    optionalText(dependency, "scope", "compile")));
        }
        return List.copyOf(result);
    }

    private static String requiredText(Element parent, String name) {
        Element child = firstDirectChild(parent, name);
        assertNotNull(child, () -> "Missing " + name + " below " + parent.getTagName());
        return child.getTextContent().trim();
    }

    private static String optionalText(Element parent, String name, String fallback) {
        Element child = firstDirectChild(parent, name);
        return child == null ? fallback : child.getTextContent().trim();
    }

    private static List<String> directChildTexts(Element parent, String childName) {
        assertNotNull(parent, "Missing parent element");
        return directChildren(parent, childName).stream()
                .map(element -> element.getTextContent().trim())
                .toList();
    }

    private static Element firstDirectChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return List.copyOf(result);
    }

    private record Dependency(String groupId, String artifactId, String scope) {

        boolean isProduction() {
            return !"test".equals(scope) && !"provided".equals(scope);
        }

        boolean isForbiddenExternal() {
            return FORBIDDEN_GROUP_PREFIXES.stream().anyMatch(groupId::startsWith);
        }
    }
}
