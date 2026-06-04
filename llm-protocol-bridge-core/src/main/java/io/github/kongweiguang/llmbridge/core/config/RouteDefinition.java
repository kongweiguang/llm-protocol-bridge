package io.github.kongweiguang.llmbridge.core.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a virtual model (route) that selects among multiple model-alias candidates
 * using a routing strategy.
 * Maps to an entry under {@code llm.bridge.routes} in YAML.
 */
@Data
public class RouteDefinition {

    /** Routing strategy: failover, priority, weighted, round-robin. */
    private String strategy = "failover";

    /** Ordered list of candidate model-alias references. */
    private List<RouteCandidateDefinition> candidates = new ArrayList<>();

    /** Default request body fields — only applied when the target body is missing the field. */
    private ObjectNode requestDefaults;

    /** Override request body fields — always applied, overwriting existing values. */
    private ObjectNode requestOverrides;
}
