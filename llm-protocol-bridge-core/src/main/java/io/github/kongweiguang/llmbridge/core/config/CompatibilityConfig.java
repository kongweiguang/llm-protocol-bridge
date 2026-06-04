package io.github.kongweiguang.llmbridge.core.config;

import lombok.Data;

/**
 * Compatibility configuration for the LLM bridge.
 * Maps to {@code llm.bridge.compatibility} in YAML.
 */
@Data
public class CompatibilityConfig {

    /** Whether to preserve unknown fields in rawExtra. Default true. */
    private boolean preserveUnknownFields = true;

    /** Policy for unsupported media types: "downgrade" or "error". */
    private String unsupportedMediaPolicy = "downgrade";

    /** Default max output tokens when the target provider requires it but client didn't specify. */
    private Integer defaultMaxOutputTokens;

    /** Whether to expose thinking/reasoning content to protocols that don't natively support it. */
    private boolean exposeThinking = false;

    /** Whether to ignore invalid thinking signatures in Anthropic responses. Default true. */
    private boolean ignoreInvalidThinkingSignature = true;
}
