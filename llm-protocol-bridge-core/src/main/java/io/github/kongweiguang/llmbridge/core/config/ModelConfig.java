package io.github.kongweiguang.llmbridge.core.config;

import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.util.Map;

/**
 * Configuration for a single upstream model.
 * Maps to an entry in {@code llm.bridge.models[]} in YAML.
 */
@Data
public class ModelConfig {

    /** The model name exposed to clients. */
    private String name;

    /** The upstream provider type. */
    private ProviderKind provider;

    /** The upstream base URL. */
    private String baseUrl;

    /** The upstream API key. */
    private String apiKey;

    /** The actual model name sent to the upstream. */
    private String model;

    /** Whether this model supports images. Defaults to true. */
    private boolean image = true;

    /** Additional request headers to send to the upstream. */
    private Map<String, String> headers;

    /** Additional body fields to deep-merge into the upstream request. */
    private ObjectNode body;

    /** Anthropic-specific: version header value. */
    private String anthropicVersion = "2023-06-01";

    /** Whether to ignore invalid thinking signatures in Anthropic responses. */
    private boolean ignoreInvalidThinkingSignature = true;

    /** Weight for weighted routing strategy (higher = more traffic). Default 1. */
    private int weight = 1;

    /** Default request body fields — only applied when the target body is missing the field. */
    private ObjectNode requestDefaults;

    /** Override request body fields — always applied, overwriting existing values. */
    private ObjectNode requestOverrides;

    /** Alias for {@link #setProvider(ProviderKind)}. */
    public void setProviderKind(ProviderKind kind) {
        this.provider = kind;
    }
}
