package io.github.aiarchguard.scanner.rule;

import static io.github.aiarchguard.scanner.rule.RuleTestModel.Link;
import static io.github.aiarchguard.scanner.rule.RuleTestModel.Node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.aiarchguard.scanner.domain.model.Dependency;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyGraphTest {

    @Test
    void indexesEdgesAndFindsCyclesDeterministically() {
        RuleTestModel.Model model = RuleTestModel.model(
                List.of(
                        new Node("A", "com.example.a", "app"),
                        new Node("B", "com.example.b", "app"),
                        new Node("C", "com.example.c", "app"),
                        new Node("D", "com.example.d", "app")),
                List.of(
                        new Link("a-b", "A", "B"),
                        new Link("b-a", "B", "A"),
                        new Link("c-c", "C", "C"),
                        new Link("b-d", "B", "D")));

        DependencyGraph graph = DependencyGraph.create(
                model.input().components(), model.input().dependencies(), RuleExecutionLimits.defaults());
        assertEquals(2, graph.cyclicStronglyConnectedComponents().size());
        assertEquals(2, graph.outgoing(model.components().get("B").id()).size());
        assertEquals(1, graph.incoming(model.components().get("D").id()).size());

        List<Dependency> shuffled = new ArrayList<>(model.input().dependencies());
        Collections.reverse(shuffled);
        DependencyGraph reordered = DependencyGraph.create(
                model.input().components().reversed(), shuffled, RuleExecutionLimits.defaults());
        assertEquals(graph.stronglyConnectedComponents(), reordered.stronglyConnectedComponents());
    }

    @Test
    void rejectsDanglingEdgesAndExecutionLimitOverruns() {
        RuleTestModel.Model model = RuleTestModel.model(
                List.of(new Node("A", "a", "app"), new Node("B", "b", "app")),
                List.of(new Link("a-b", "A", "B"), new Link("b-a", "B", "A")));

        RuleEngineException nodeLimit = assertThrows(
                RuleEngineException.class,
                () -> DependencyGraph.create(
                        model.input().components(),
                        model.input().dependencies(),
                        new RuleExecutionLimits(1, 2, 1)));
        assertEquals("rule.limit.nodes", nodeLimit.code());

        RuleEngineException edgeLimit = assertThrows(
                RuleEngineException.class,
                () -> DependencyGraph.create(
                        model.input().components(),
                        model.input().dependencies(),
                        new RuleExecutionLimits(2, 1, 1)));
        assertEquals("rule.limit.edges", edgeLimit.code());

        Dependency valid = model.input().dependencies().getFirst();
        Dependency dangling = new Dependency(
                valid.id(),
                valid.sourceId(),
                "component_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                valid.kind(),
                valid.evidenceIds(),
                valid.extensions());
        RuleEngineException danglingEdge = assertThrows(
                RuleEngineException.class,
                () -> DependencyGraph.create(
                        model.input().components(), List.of(dangling), RuleExecutionLimits.defaults()));
        assertEquals("rule.input.dangling-dependency", danglingEdge.code());
    }
}
