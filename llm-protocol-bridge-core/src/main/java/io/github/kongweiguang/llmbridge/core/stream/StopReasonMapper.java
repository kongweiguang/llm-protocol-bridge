package io.github.kongweiguang.llmbridge.core.stream;

/**
 * Maps stop reasons between normalized, OpenAI, and Anthropic formats.
 */
public final class StopReasonMapper {

    private StopReasonMapper() {
    }

    /**
     * Converts an OpenAI finish_reason to the normalized stop reason.
     *
     * @param finishReason OpenAI finish_reason
     * @return normalized stop reason
     */
    public static String toNormalizedFromOpenAi(String finishReason) {
        if (finishReason == null) return "end_turn";
        return switch (finishReason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "tool_calls", "function_call" -> "tool_use";
            case "content_filter" -> "refusal";
            default -> "end_turn";
        };
    }

    /**
     * Converts an Anthropic stop_reason to the normalized stop reason.
     *
     * @param stopReason Anthropic stop_reason
     * @return normalized stop reason
     */
    public static String toNormalizedFromAnthropic(String stopReason) {
        if (stopReason == null) return "end_turn";
        return switch (stopReason) {
            case "end_turn" -> "end_turn";
            case "max_tokens" -> "max_tokens";
            case "stop_sequence" -> "stop_sequence";
            case "tool_use" -> "tool_use";
            case "pause_turn" -> "pause_turn";
            case "refusal" -> "refusal";
            default -> stopReason;
        };
    }

    /**
     * Converts a normalized stop reason to OpenAI finish_reason.
     *
     * @param stopReason normalized stop reason
     * @return OpenAI finish_reason
     */
    public static String toOpenAiFinishReason(String stopReason) {
        if (stopReason == null) return "stop";
        return switch (stopReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            case "refusal" -> "content_filter";
            case "stop_sequence" -> "stop";
            default -> "stop";
        };
    }

    /**
     * Converts a normalized stop reason to Anthropic stop_reason.
     *
     * @param stopReason normalized stop reason
     * @return Anthropic stop_reason
     */
    public static String toAnthropicStopReason(String stopReason) {
        if (stopReason == null) return "end_turn";
        return switch (stopReason) {
            case "end_turn" -> "end_turn";
            case "max_tokens" -> "max_tokens";
            case "tool_use" -> "tool_use";
            case "refusal" -> "refusal";
            case "stop_sequence" -> "stop_sequence";
            default -> stopReason;
        };
    }

    /**
     * Converts an OpenAI Responses status to the normalized stop reason.
     *
     * @param status Responses status
     * @return normalized stop reason
     */
    public static String toNormalizedFromResponsesStatus(String status) {
        if (status == null) return "end_turn";
        return switch (status) {
            case "completed" -> "end_turn";
            case "incomplete" -> "max_tokens";
            case "failed" -> "refusal";
            case "in_progress" -> "in_progress";
            case "queued" -> "queued";
            case "cancelled" -> "cancelled";
            default -> status;
        };
    }

    /**
     * Converts a normalized stop reason to Responses status.
     *
     * @param stopReason normalized stop reason
     * @return Responses status
     */
    public static String toResponsesStatus(String stopReason) {
        if (stopReason == null) return "completed";
        return switch (stopReason) {
            case "end_turn" -> "completed";
            case "max_tokens" -> "incomplete";
            case "refusal" -> "failed";
            case "in_progress" -> "in_progress";
            case "queued" -> "queued";
            case "cancelled" -> "cancelled";
            default -> "completed";
        };
    }
}
