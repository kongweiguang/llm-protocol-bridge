package io.github.kongweiguang.llmbridge.core.config;

import lombok.Data;

/**
 * A single candidate within a route definition.
 */
@Data
public class RouteCandidateDefinition {

    /** Reference to a model-alias defined in llm.bridge.model-aliases. */
    private String modelRef;
}
