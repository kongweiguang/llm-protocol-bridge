package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Represents the tool_choice parameter in the normalized model.
 * Can be a string ("auto", "none", "required") or an object specifying a function.
 */
@Data
public class CanonicalToolChoice {

    /** The tool choice value - either a string or a structured object. */
    private JsonNode value;

    public CanonicalToolChoice(JsonNode value) {
        this.value = value;
    }

    /**
     * Returns true if this is a simple string choice (auto/none/required).
     */
    public boolean isSimple() {
        return value != null && value.isTextual();
    }

    /**
     * Returns the string value if this is a simple choice.
     */
    public String asString() {
        return value != null && value.isTextual() ? value.asText() : null;
    }
}
