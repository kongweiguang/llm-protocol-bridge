package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.util.List;

/**
 * Unified response model that can represent responses from any supported protocol.
 * All protocol codecs convert to/from this intermediate representation.
 * Unknown fields are preserved in rawExtra.
 */
@Data
public class CanonicalResponse {

    /** Unique response identifier. */
    private String id;

    /** The model name requested by the client. */
    private String requestedModel;

    /** The actual upstream model name that generated the response. */
    private String upstreamModel;

    /** Unix timestamp when the response was created. */
    private Long created;

    /** The output messages from the model. */
    private List<CanonicalMessage> outputMessages;

    /** Tool calls at the response level (for protocols that support it). */
    private List<CanonicalToolCall> toolCalls;

    /** The reason the model stopped generating (normalized). */
    private String stopReason;

    /** Token usage information. */
    private CanonicalUsage usage;

    /** Additional response-level metadata. */
    private ObjectNode responseMetadata;

    /** Extra fields from the raw response that don't map to normalized fields. */
    private ObjectNode rawExtra;

    /**
     * Returns the model name, preferring upstreamModel if set.
     */
    public String getModel() {
        return upstreamModel != null ? upstreamModel : requestedModel;
    }

    /**
     * Sets both requestedModel and upstreamModel to the same value.
     */
    public void setModel(String model) {
        this.upstreamModel = model;
    }
}
