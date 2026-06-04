package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.util.List;

/**
 * Unified request model that can represent requests from any supported protocol.
 * All protocol codecs convert to/from this intermediate representation.
 * Unknown fields from the source protocol are preserved in rawExtra.
 */
@Data
public class CanonicalRequest {

    /** The model name requested by the client. */
    private String requestedModel;

    /** The actual upstream model name to send. */
    private String upstreamModel;

    /** The conversation messages. */
    private List<CanonicalMessage> messages;

    /** Available tools/functions. */
    private List<CanonicalTool> tools;

    /** Tool choice configuration. */
    private CanonicalToolChoice toolChoice;

    /** Whether to allow parallel tool calls. */
    private Boolean parallelToolCalls;

    /** Maximum output tokens. */
    private Integer maxOutputTokens;

    /** Sampling temperature. */
    private Double temperature;

    /** Nucleus sampling parameter. */
    private Double topP;

    /** Frequency penalty. */
    private Double frequencyPenalty;

    /** Presence penalty. */
    private Double presencePenalty;

    /** Stop sequences. */
    private List<String> stopSequences;

    /** Number of completions to generate. */
    private Integer n;

    /** Random seed for reproducibility. */
    private Long seed;

    /** Whether to stream the response. */
    private Boolean stream;

    /** Stream options (e.g., include_usage). */
    private ObjectNode streamOptions;

    /** Response format specification (e.g., JSON mode). */
    private ObjectNode responseFormat;

    /** Reasoning/thinking configuration. */
    private ObjectNode reasoning;

    /** Text configuration (Responses API). */
    private ObjectNode text;

    /** Additional metadata. */
    private ObjectNode metadata;

    /** Previous response ID (Responses API multi-turn). */
    private String previousResponseId;

    /** Whether to store the response (Responses API). */
    private Boolean store;

    /** User identifier (OpenAI). */
    private String user;

    /** Provider-specific options that should be passed through. */
    private ObjectNode providerOptions;

    /** Audio output configuration (OpenAI Chat / Responses). */
    private ObjectNode audio;

    /** Output modalities (e.g. ["text","audio"]). */
    private List<String> modalities;

    /** Whether to run in background (OpenAI Responses). */
    private Boolean background;

    /** Conversation reference (OpenAI Responses). */
    private ObjectNode conversation;

    /** Safety identifier (OpenAI Responses). */
    private String safetyIdentifier;

    /** Prompt cache key (OpenAI). */
    private String promptCacheKey;

    /** Prompt template (OpenAI Responses). */
    private ObjectNode prompt;

    /** Predicted content for speculative decoding (OpenAI Chat). */
    private ObjectNode prediction;

    /** Reasoning effort level (OpenAI Chat/Responses). */
    private String reasoningEffort;

    /** Web search options (OpenAI Chat). */
    private ObjectNode webSearchOptions;

    /** Logit bias mapping (OpenAI Chat). */
    private ObjectNode logitBias;

    /** Whether to return logprobs (OpenAI Chat). */
    private Boolean logprobs;

    /** Number of top logprobs to return (OpenAI Chat). */
    private Integer topLogprobs;

    /** Top-K sampling (Anthropic). */
    private Integer topK;

    /** Service tier (Anthropic / OpenAI). */
    private String serviceTier;

    /** Inference geographic region (Anthropic). */
    private String inferenceGeo;

    /** Inference speed (Anthropic). */
    private String speed;

    /** Container reference (Anthropic). */
    private ObjectNode container;

    /** MCP server configurations (Anthropic). */
    private List<ObjectNode> mcpServers;

    /** Context management config (Anthropic Beta). */
    private ObjectNode contextManagement;

    /** Cache control directives to apply (Anthropic). */
    private ObjectNode cacheControl;

    /** Extra fields from the raw request that don't map to normalized fields. */
    private ObjectNode rawExtra;

    /**
     * Returns the model name, preferring upstreamModel if set.
     */
    public String getModel() {
        return upstreamModel != null ? upstreamModel : requestedModel;
    }

    /**
     * Sets the upstream model name.
     */
    public void setModel(String model) {
        this.upstreamModel = model;
    }
}
