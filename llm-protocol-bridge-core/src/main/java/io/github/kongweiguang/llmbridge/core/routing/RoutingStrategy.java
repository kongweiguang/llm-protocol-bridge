package io.github.kongweiguang.llmbridge.core.routing;

/**
 * Routing strategy for selecting among fallback candidates.
 */
public enum RoutingStrategy {

    /** Try candidates in order; fall through on failure. */
    FAILOVER,

    /** Always prefer the first candidate (same as failover for ordered lists). */
    PRIORITY,

    /** Random selection weighted by candidate weight. */
    WEIGHTED,

    /** Cycle through candidates round-robin. */
    ROUND_ROBIN;

    /**
     * Parses a strategy name string, defaulting to FAILOVER.
     */
    public static RoutingStrategy parse(String name) {
        if (name == null) return FAILOVER;
        return switch (name.toLowerCase().replace("-", "").replace("_", "")) {
            case "failover" -> FAILOVER;
            case "priority" -> PRIORITY;
            case "weighted" -> WEIGHTED;
            case "roundrobin" -> ROUND_ROBIN;
            default -> FAILOVER;
        };
    }
}
