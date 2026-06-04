package io.github.kongweiguang.llmbridge.core.canonical;

import lombok.Data;

/**
 * A tool result content part in a normalized message.
 * Represents the output of a tool invocation.
 */
@Data
public class ToolResultContentPart implements CanonicalContentPart {

    /** The ID of the tool call this result corresponds to. */
    private String toolCallId;

    /** The textual content of the tool result. */
    private String content;

    /** Whether this result represents an error. */
    private Boolean isError;

    @Override
    public String type() {
        return "tool_result";
    }
}
