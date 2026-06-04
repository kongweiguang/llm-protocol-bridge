package io.github.kongweiguang.llmbridge.core.stream;

import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.json.JacksonUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * Maps {@link BridgeException} to protocol-specific SSE error events.
 */
public final class StreamErrorMapper {

    private StreamErrorMapper() {
    }

    /**
     * Converts a BridgeException to an SSE error event for the specified protocol.
     *
     * @param e      the bridge exception
     * @param format the target API format
     * @return the error SSE event
     */
    public static SseFrame toErrorEvent(BridgeException e, ApiProtocol format) {
        return switch (format) {
            case OPENAI_CHAT_COMPLETIONS -> toOpenAiChatError(e);
            case OPENAI_RESPONSES -> toOpenAiResponsesError(e);
            case ANTHROPIC_MESSAGES -> toAnthropicError(e);
        };
    }

    /**
     * Converts a BridgeException to an OpenAI Chat error SSE event.
     *
     * @param e the bridge exception
     * @return the error SSE event followed by [DONE]
     */
    public static SseFrame toOpenAiChatError(BridgeException e) {
        ObjectNode errorNode = JacksonUtil.objectNode();
        ObjectNode errorObj = errorNode.putObject("error");
        errorObj.put("message", e.getMessage());
        errorObj.put("type", e.getError().getType() != null ? e.getError().getType() : "api_error");
        errorObj.put("code", e.getError().getCode() != null ? e.getError().getCode() : "upstream_error");
        return new SseFrame(null, errorNode.toString());
    }

    /**
     * Returns the [DONE] event for OpenAI Chat.
     *
     * @return the [DONE] SSE event
     */
    public static SseFrame doneEvent() {
        return new SseFrame(null, "[DONE]");
    }

    /**
     * Converts a BridgeException to an OpenAI Responses error SSE event.
     *
     * @param e the bridge exception
     * @return the error SSE event
     */
    public static SseFrame toOpenAiResponsesError(BridgeException e) {
        ObjectNode root = JacksonUtil.objectNode();
        root.put("type", "error");
        ObjectNode errorObj = root.putObject("error");
        errorObj.put("message", e.getMessage());
        errorObj.put("code", e.getError().getCode() != null ? e.getError().getCode() : "upstream_error");
        SseFrame event = new SseFrame("error", root.toString());
        return event;
    }

    /**
     * Converts a BridgeException to an Anthropic error SSE event.
     *
     * @param e the bridge exception
     * @return the error SSE event
     */
    public static SseFrame toAnthropicError(BridgeException e) {
        ObjectNode root = JacksonUtil.objectNode();
        root.put("type", "error");
        ObjectNode errorObj = root.putObject("error");
        errorObj.put("type", e.getError().getType() != null ? e.getError().getType() : "api_error");
        errorObj.put("message", e.getMessage());
        SseFrame event = new SseFrame("error", root.toString());
        return event;
    }
}
