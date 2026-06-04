package io.github.kongweiguang.llmbridge.core.stream;

import io.github.kongweiguang.llmbridge.core.canonical.CanonicalRole;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

/**
 * Represents a normalized stream event that can be converted to any protocol's stream format.
 * Contains all fields needed to represent stream events across OpenAI Chat, Responses, and Anthropic.
 */
@Data
public class CanonicalStreamEvent {

    /** The type of this stream event. */
    private CanonicalStreamEventType type;

    /** Response ID for this stream. */
    private String responseId;

    /** Message ID (for Anthropic message_start). */
    private String messageId;

    /** Choice/output index. */
    private Integer choiceIndex;

    /** Content block index. */
    private Integer contentIndex;

    /** Tool call index. */
    private Integer toolIndex;

    /** Role (for MESSAGE_START). */
    private CanonicalRole role;

    /** Text delta content. */
    private String deltaText;

    /** Refusal delta content. */
    private String refusalDelta;

    /** Tool call ID. */
    private String toolCallId;

    /** Tool name. */
    private String toolName;

    /** Tool arguments delta (incremental JSON string). */
    private String toolArgumentsDelta;

    /** Tool arguments snapshot (complete JSON, only on TOOL_CALL_DONE). */
    private ObjectNode toolArgumentsSnapshot;

    /** Thinking delta content. */
    private String thinkingDelta;

    /** Thinking signature. */
    private String thinkingSignature;

    /** Stop reason. */
    private String stopReason;

    /** Usage information. */
    private CanonicalUsage usage;

    /** Raw upstream event data for pass-through. */
    private JsonNode raw;

    public CanonicalStreamEvent() {
    }

    public CanonicalStreamEvent(CanonicalStreamEventType type) {
        this.type = type;
    }
}
