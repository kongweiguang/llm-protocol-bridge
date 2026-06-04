package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * A tool/function call content part in a normalized message.
 * Represents a request from the model to invoke a tool.
 */
@Data
public class ToolCallContentPart implements CanonicalContentPart {

    /** Unique identifier for this tool call. */
    private String id;

    /** Name of the tool/function to call. */
    private String name;

    /** Arguments to pass to the tool, as a JSON node. */
    private JsonNode arguments;

    @Override
    public String type() {
        return "tool_call";
    }
}
