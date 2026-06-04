package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.util.List;

/**
 * A unified message model that can represent messages from any supported protocol.
 * Contains role, content parts, optional tool calls, and metadata.
 * Unknown fields are preserved in rawExtra.
 */
@Data
public class CanonicalMessage {

    /** The role of the message sender. */
    private CanonicalRole role;

    /** The content parts of this message. */
    private List<CanonicalContentPart> content;

    /** Tool calls made by the assistant in this message. */
    private List<CanonicalToolCall> toolCalls;

    /** The tool call ID this message is responding to (for TOOL role messages). */
    private String toolCallId;

    /** Optional name for the message sender. */
    private String name;

    /** Reasoning content (for thinking models like o1, mimo, claude). */
    private String reasoningContent;

    /** Audio output (for OpenAI audio responses). */
    private ObjectNode audio;

    /** Extra fields from the raw message that don't map to normalized fields. */
    private ObjectNode rawExtra;

    public CanonicalMessage() {
    }

    public CanonicalMessage(CanonicalRole role, List<CanonicalContentPart> content) {
        this.role = role;
        this.content = content;
    }
}
