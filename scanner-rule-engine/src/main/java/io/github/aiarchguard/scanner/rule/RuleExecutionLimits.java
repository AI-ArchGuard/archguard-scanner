package io.github.aiarchguard.scanner.rule;

public record RuleExecutionLimits(int maxNodes, int maxEdges, int maxFindings) {

    public static final int DEFAULT_MAX_NODES = 100_000;
    public static final int DEFAULT_MAX_EDGES = 1_000_000;
    public static final int DEFAULT_MAX_FINDINGS = 10_000;

    public RuleExecutionLimits {
        if (maxNodes < 1 || maxNodes > DEFAULT_MAX_NODES) {
            throw new IllegalArgumentException("maxNodes must be between 1 and " + DEFAULT_MAX_NODES);
        }
        if (maxEdges < 1 || maxEdges > DEFAULT_MAX_EDGES) {
            throw new IllegalArgumentException("maxEdges must be between 1 and " + DEFAULT_MAX_EDGES);
        }
        if (maxFindings < 1 || maxFindings > DEFAULT_MAX_FINDINGS) {
            throw new IllegalArgumentException("maxFindings must be between 1 and " + DEFAULT_MAX_FINDINGS);
        }
    }

    public static RuleExecutionLimits defaults() {
        return new RuleExecutionLimits(DEFAULT_MAX_NODES, DEFAULT_MAX_EDGES, DEFAULT_MAX_FINDINGS);
    }
}
