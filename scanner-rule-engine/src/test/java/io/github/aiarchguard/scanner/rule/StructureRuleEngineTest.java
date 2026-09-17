package io.github.aiarchguard.scanner.rule;

import static io.github.aiarchguard.scanner.rule.RuleTestModel.Link;
import static io.github.aiarchguard.scanner.rule.RuleTestModel.Node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aiarchguard.scanner.domain.model.Finding;
import io.github.aiarchguard.scanner.domain.model.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructureRuleEngineTest {

    private final StructureRuleEngine engine = new StructureRuleEngine();

    @Test
    void reportsDeniedAndUnlistedPackageDependenciesAtSelectorBoundaries() {
        RuleTestModel.Model model = RuleTestModel.model(
                List.of(
                        new Node("Source", "com.app.api", "app"),
                        new Node("Direct", "com.target.one", "app"),
                        new Node("Nested", "com.target.one.deep", "app"),
                        new Node("Denied", "com.forbidden", "app")),
                List.of(
                        new Link("allowed", "Source", "Direct"),
                        new Link("unlisted", "Source", "Nested"),
                        new Link("denied", "Source", "Denied")));
        IllegalPackageDependencyConfig config = new IllegalPackageDependencyConfig(
                List.of(new PackagePolicy(
                        new PackageSelector("com.app.**"),
                        List.of(new PackageSelector("com.target.*")),
                        List.of(new PackageSelector("com.forbidden")))),
                Severity.HIGH);

        List<Finding> findings = engine.execute(model.input(), List.of(config), RuleExecutionLimits.defaults()).findings();

        assertEquals(2, findings.size());
        assertTrue(findings.stream().allMatch(finding -> finding.rule().id().equals("archguard.illegal-package-dependency")));
        assertTrue(findings.stream().anyMatch(finding -> finding.message().contains("is denied")));
        assertTrue(findings.stream().anyMatch(finding -> finding.message().contains("is not allowed")));
    }

    @Test
    void enforcesLayerDirectionAndOptionalLayerSkipping() {
        RuleTestModel.Model model = RuleTestModel.model(
                List.of(
                        new Node("Api", "com.app.api", "app"),
                        new Node("Service", "com.app.service", "app"),
                        new Node("Data", "com.app.data", "app")),
                List.of(
                        new Link("forward", "Api", "Service"),
                        new Link("skip", "Api", "Data"),
                        new Link("reverse", "Data", "Api")));
        List<ArchitectureLayer> layers = List.of(
                layer("api", "com.app.api"),
                layer("service", "com.app.service"),
                layer("data", "com.app.data"));

        List<Finding> strict = engine.execute(
                        model.input(),
                        List.of(new LayeredArchitectureConfig(layers, false, Severity.HIGH)),
                        RuleExecutionLimits.defaults())
                .findings();
        List<Finding> skippingAllowed = engine.execute(
                        model.input(),
                        List.of(new LayeredArchitectureConfig(layers, true, Severity.HIGH)),
                        RuleExecutionLimits.defaults())
                .findings();

        assertEquals(2, strict.size());
        assertEquals(1, skippingAllowed.size());
        assertTrue(skippingAllowed.getFirst().message().contains("data"));
        assertTrue(skippingAllowed.getFirst().message().contains("api"));
    }

    @Test
    void rejectsPackagesThatMatchMultipleLayers() {
        RuleTestModel.Model model = RuleTestModel.model(
                List.of(new Node("A", "com.app.api", "app"), new Node("B", "com.other", "app")),
                List.of(new Link("a-b", "A", "B")));
        LayeredArchitectureConfig config = new LayeredArchitectureConfig(
                List.of(layer("all", "com.app.**"), layer("api", "com.app.api")), false, Severity.HIGH);

        RuleEngineException exception = assertThrows(
                RuleEngineException.class,
                () -> engine.execute(model.input(), List.of(config), RuleExecutionLimits.defaults()));
        assertEquals("rule.config.layer-overlap", exception.code());
    }

    @Test
    void detectsComponentPackageAndModuleCyclesWithClosedEvidence() {
        RuleTestModel.Model model = RuleTestModel.model(
                List.of(
                        new Node("A", "com.a", "module-a"),
                        new Node("B", "com.b", "module-b"),
                        new Node("C", "com.c", "module-c"),
                        new Node("Self", "com.self", "module-self"),
                        new Node("D", "com.d", "module-d"),
                        new Node("E", "com.e", "module-e")),
                List.of(
                        new Link("a-b", "A", "B"),
                        new Link("b-c", "B", "C"),
                        new Link("c-a", "C", "A"),
                        new Link("self", "Self", "Self"),
                        new Link("d-e", "D", "E")));

        List<Finding> componentFindings = cycle(model, CycleScope.COMPONENT);
        List<Finding> packageFindings = cycle(model, CycleScope.PACKAGE);
        List<Finding> moduleFindings = cycle(model, CycleScope.MODULE);

        assertEquals(2, componentFindings.size());
        assertEquals(1, packageFindings.size());
        assertEquals(1, moduleFindings.size());
        Finding multiNodeCycle = componentFindings.stream()
                .filter(finding -> finding.evidenceIds().size() == 3)
                .findFirst()
                .orElseThrow();
        assertEquals(
                model.input().dependencies().stream()
                        .filter(dependency -> !dependency.sourceId().equals(dependency.targetId()))
                        .filter(dependency -> !dependency.id().equals(model.dependencies().get("d-e").id()))
                        .flatMap(dependency -> dependency.evidenceIds().stream())
                        .sorted()
                        .toList(),
                multiNodeCycle.evidenceIds());
    }

    @Test
    void packageSelectorsSupportDefaultAndUnicodePackagesAndRejectAmbiguousPolicies() {
        assertTrue(new PackageSelector("公司.架构.**").matches("公司.架构.规则"));
        assertTrue(new PackageSelector("<default>").matches("<default>"));
        assertThrows(IllegalArgumentException.class, () -> new PackageSelector("com..example"));

        PackagePolicy policy = new PackagePolicy(
                new PackageSelector("com.source"), List.of(), List.of(new PackageSelector("com.target")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IllegalPackageDependencyConfig(List.of(policy, policy), Severity.HIGH));
    }

    @Test
    void producesIdenticalFindingsForReorderedInputAndConfiguration() {
        RuleTestModel.Model model = RuleTestModel.model(
                List.of(
                        new Node("A", "com.a", "module-a"),
                        new Node("B", "com.b", "module-b")),
                List.of(new Link("a-b", "A", "B"), new Link("b-a", "B", "A")));
        List<StructureRuleConfiguration> configs = List.of(
                new DependencyCycleConfig(CycleScope.COMPONENT, Severity.HIGH),
                new IllegalPackageDependencyConfig(
                        List.of(new PackagePolicy(
                                new PackageSelector("com.a"),
                                List.of(),
                                List.of(new PackageSelector("com.b")))),
                        Severity.MEDIUM));

        List<Finding> first = engine.execute(model.input(), configs, RuleExecutionLimits.defaults()).findings();
        List<Finding> second = engine.execute(
                        model.reversed().input(), configs.reversed(), RuleExecutionLimits.defaults())
                .findings();

        assertEquals(first, second);
        assertNotEquals(first.get(0).id(), first.get(1).id());
    }

    @Test
    void rejectsDuplicateRuleConfigurationAndFindingLimitOverrun() {
        RuleTestModel.Model model = RuleTestModel.model(
                List.of(
                        new Node("A", "a", "app"),
                        new Node("B", "b", "app"),
                        new Node("C", "c", "app")),
                List.of(new Link("a-b", "A", "B"), new Link("a-c", "A", "C")));
        IllegalPackageDependencyConfig config = new IllegalPackageDependencyConfig(
                List.of(new PackagePolicy(
                        new PackageSelector("a"),
                        List.of(),
                        List.of(new PackageSelector("b"), new PackageSelector("c")))),
                Severity.HIGH);

        RuleEngineException duplicate = assertThrows(
                RuleEngineException.class,
                () -> engine.execute(model.input(), List.of(config, config), RuleExecutionLimits.defaults()));
        assertEquals("rule.config.duplicate-rule", duplicate.code());

        RuleEngineException limited = assertThrows(
                RuleEngineException.class,
                () -> engine.execute(
                        model.input(), List.of(config), new RuleExecutionLimits(3, 2, 1)));
        assertEquals("rule.limit.findings", limited.code());
    }

    private List<Finding> cycle(RuleTestModel.Model model, CycleScope scope) {
        return engine.execute(
                        model.input(),
                        List.of(new DependencyCycleConfig(scope, Severity.HIGH)),
                        RuleExecutionLimits.defaults())
                .findings();
    }

    private static ArchitectureLayer layer(String name, String pattern) {
        return new ArchitectureLayer(name, List.of(new PackageSelector(pattern)));
    }
}
