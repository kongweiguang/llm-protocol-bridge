package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

/**
 * Represents token usage in the normalized model.
 * Aggregates input, output, total token counts, and detailed breakdowns.
 * Unknown fields are preserved in rawExtra.
 */
@Data
public class CanonicalUsage {

    /** Number of input/prompt tokens. */
    private Integer inputTokens;

    /** Number of output/completion tokens. */
    private Integer outputTokens;

    /** Total tokens (input + output). */
    private Integer totalTokens;

    /** Number of cached input tokens (OpenAI prompt_tokens_details.cached_tokens / Anthropic cache_read_input_tokens). */
    private Integer cachedInputTokens;

    /** Anthropic cache_creation_input_tokens (newly created cache). */
    private Integer cacheCreationInputTokens;

    /** Anthropic cache_read_input_tokens (cache hit, normalized form). */
    private Integer cacheReadInputTokens;

    /** Number of reasoning tokens (for models with chain-of-thought). */
    private Integer reasoningTokens;

    /** Number of audio input tokens. */
    private Integer audioInputTokens;

    /** Number of audio output tokens. */
    private Integer audioOutputTokens;

    /** Number of web search requests made (Anthropic server tool). */
    private Integer webSearchRequests;

    /** Service tier (Anthropic / OpenAI). */
    private String serviceTier;

    /** Extra fields from the raw usage that don't map to normalized fields. */
    private ObjectNode rawExtra;
}
