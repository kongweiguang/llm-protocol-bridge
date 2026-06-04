package io.github.kongweiguang.llmbridge.core.error;

import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Maps {@link CanonicalError} to protocol-specific error response formats.
 */
public final class ErrorMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorMapper() {
    }

    /**
     * Converts a {@link CanonicalError} to the specified protocol's error format.
     *
     * @param error  the normalized error
     * @param format the target API format
     * @return the error response as a JSON node
     */
    public static JsonNode toResponse(CanonicalError error, ApiProtocol format) {
        return switch (format) {
            case OPENAI_CHAT_COMPLETIONS, OPENAI_RESPONSES -> toOpenAiFormat(error);
            case ANTHROPIC_MESSAGES -> toAnthropicFormat(error);
        };
    }

    /**
     * Converts a {@link CanonicalError} to OpenAI error format.
     *
     * @param error the normalized error
     * @return OpenAI-formatted error JSON
     */
    public static JsonNode toOpenAiFormat(CanonicalError error) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode errorNode = MAPPER.createObjectNode();
        errorNode.put("message", error.getMessage());
        if (error.getType() != null) {
            errorNode.put("type", error.getType());
        }
        if (error.getCode() != null) {
            errorNode.put("code", error.getCode());
        }
        root.set("error", errorNode);
        return root;
    }

    /**
     * Converts a {@link CanonicalError} to Anthropic error format.
     *
     * @param error the normalized error
     * @return Anthropic-formatted error JSON
     */
    public static JsonNode toAnthropicFormat(CanonicalError error) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "error");
        ObjectNode errorNode = MAPPER.createObjectNode();
        if (error.getType() != null) {
            errorNode.put("type", error.getType());
        }
        errorNode.put("message", error.getMessage());
        root.set("error", errorNode);
        return root;
    }
}
