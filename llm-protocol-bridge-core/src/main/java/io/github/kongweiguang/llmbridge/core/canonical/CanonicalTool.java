package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

/**
 * Represents a tool/function definition in the normalized model.
 * Contains the function name, description, and parameter schema.
 * Unknown fields are preserved in rawExtra.
 */
@Data
public class CanonicalTool {

    /** Tool type (e.g., "function"). */
    private String type;

    /** The function name. */
    private String name;

    /** Human-readable description of the function. */
    private String description;

    /** JSON Schema for the function parameters. */
    private JsonNode inputSchema;

    /** Whether to enable strict schema adherence. */
    private Boolean strict;

    /** Extra fields from the raw tool definition. */
    private ObjectNode rawExtra;
}
