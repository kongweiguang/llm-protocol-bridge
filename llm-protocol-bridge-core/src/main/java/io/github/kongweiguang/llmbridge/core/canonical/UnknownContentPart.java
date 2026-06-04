package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * An unknown/unrecognized content part in a normalized message.
 * Preserves the raw JSON of content blocks that don't map to known types.
 * This ensures no fields are silently dropped during protocol conversion.
 */
@Data
public class UnknownContentPart implements CanonicalContentPart {

    /** The original type string from the source protocol. */
    private String originalType;

    /** The raw JSON of the unknown content block. */
    private JsonNode raw;

    public UnknownContentPart(String originalType, JsonNode raw) {
        this.originalType = originalType;
        this.raw = raw;
    }

    @Override
    public String type() {
        return "unknown";
    }
}
