package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

/**
 * Represents a tool/function call in the normalized model.
 * Contains the tool call ID, function name, parsed arguments, and metadata.
 * Unknown fields are preserved in rawExtra.
 */
@Data
public class CanonicalToolCall {

    /** Unique identifier for this tool call. */
    private String id;

    /** Tool call type (e.g., "function"). */
    private String type;

    /** Name of the function/tool to call. */
    private String name;

    /** Arguments as a JSON node (parsed from the upstream's JSON string). */
    private JsonNode arguments;

    /** Original raw arguments string (preserved even if JSON parsing fails). */
    private String rawArguments;

    /** Index of this tool call in a multi-tool-call response. */
    private Integer index;

    /** Extra fields from the raw tool call that don't map to normalized fields. */
    private ObjectNode rawExtra;
}
