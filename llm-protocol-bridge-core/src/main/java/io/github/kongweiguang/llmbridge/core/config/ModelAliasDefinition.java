package io.github.kongweiguang.llmbridge.core.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

/**
 * Defines a client-facing model name that maps to an upstream model via a provider.
 * Maps to an entry under {@code llm.bridge.model-aliases} in YAML.
 */
@Data
public class ModelAliasDefinition {

    /** Reference to a provider defined in llm.bridge.providers. */
    private String providerRef;

    /** The actual model name sent to the upstream provider. */
    private String upstreamModel;

    /** Default request body fields — only applied when the target body is missing the field. */
    private ObjectNode requestDefaults;

    /** Override request body fields — always applied, overwriting existing values. */
    private ObjectNode requestOverrides;
}
