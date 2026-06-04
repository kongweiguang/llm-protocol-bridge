package io.github.kongweiguang.llmbridge.core.stream;

import io.github.kongweiguang.llmbridge.core.canonical.CanonicalUsage;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the state of a stream for aggregation and final response construction.
 * Used to accumulate tool call arguments, text content, and usage information.
 */
@Getter
@Setter
public class StreamStateTracker {

    private String responseId;
    private String model;
    private long created;

    private final Map<Integer, ToolCallStateTracker> toolCalls = new HashMap<>();
    private final Map<Integer, ContentBlockStateTracker> contentBlocks = new HashMap<>();

    private CanonicalUsage usage;
    private String stopReason;

    /** Whether any content delta has been emitted (used for fallback decisions). */
    private boolean contentEmitted = false;

    /**
     * Applies a normalized stream event to update the state.
     *
     * @param event the stream event
     */
    public void apply(CanonicalStreamEvent event) {
        if (event == null || event.getType() == null) return;

        switch (event.getType()) {
            case START -> {
                if (event.getResponseId() != null) responseId = event.getResponseId();
                mergeUsage(event.getUsage());
            }
            case MESSAGE_START -> {
                if (event.getResponseId() != null) responseId = event.getResponseId();
                mergeUsage(event.getUsage());
            }
            case TEXT_DELTA -> {
                contentEmitted = true;
            }
            case TOOL_CALL_START -> {
                int idx = event.getToolIndex() != null ? event.getToolIndex() : 0;
                ToolCallStateTracker tcs = new ToolCallStateTracker(idx, event.getToolCallId(), event.getToolName());
                toolCalls.put(idx, tcs);
            }
            case TOOL_ARGUMENTS_DELTA -> {
                contentEmitted = true;
                int idx = event.getToolIndex() != null ? event.getToolIndex() : 0;
                ToolCallStateTracker tcs = toolCalls.get(idx);
                if (tcs != null && event.getToolArgumentsDelta() != null) {
                    tcs.appendArguments(event.getToolArgumentsDelta());
                }
            }
            case TOOL_CALL_DONE -> {
                int idx = event.getToolIndex() != null ? event.getToolIndex() : 0;
                ToolCallStateTracker tcs = toolCalls.get(idx);
                if (tcs != null) {
                    tcs.parseArguments();
                }
            }
            case CONTENT_BLOCK_START -> {
                int idx = event.getContentIndex() != null ? event.getContentIndex() : 0;
                ContentBlockStateTracker cbs = new ContentBlockStateTracker(idx);
                contentBlocks.put(idx, cbs);
            }
            case THINKING_DELTA -> {
                contentEmitted = true;
            }
            case USAGE_DELTA -> {
                mergeUsage(event.getUsage());
            }
            case MESSAGE_DELTA -> {
                if (event.getStopReason() != null) stopReason = event.getStopReason();
                mergeUsage(event.getUsage());
            }
            case DONE -> {
                if (event.getStopReason() != null) stopReason = event.getStopReason();
                mergeUsage(event.getUsage());
            }
            default -> {}
        }
    }

    private void mergeUsage(CanonicalUsage incoming) {
        if (incoming == null) return;
        if (usage == null) {
            usage = new CanonicalUsage();
        }
        if (incoming.getInputTokens() != null) usage.setInputTokens(incoming.getInputTokens());
        if (incoming.getOutputTokens() != null) usage.setOutputTokens(incoming.getOutputTokens());
        if (incoming.getTotalTokens() != null) usage.setTotalTokens(incoming.getTotalTokens());
        if (incoming.getCachedInputTokens() != null) usage.setCachedInputTokens(incoming.getCachedInputTokens());
        if (incoming.getCacheCreationInputTokens() != null) {
            usage.setCacheCreationInputTokens(incoming.getCacheCreationInputTokens());
        }
        if (incoming.getCacheReadInputTokens() != null) usage.setCacheReadInputTokens(incoming.getCacheReadInputTokens());
        if (incoming.getReasoningTokens() != null) usage.setReasoningTokens(incoming.getReasoningTokens());
        if (incoming.getAudioInputTokens() != null) usage.setAudioInputTokens(incoming.getAudioInputTokens());
        if (incoming.getAudioOutputTokens() != null) usage.setAudioOutputTokens(incoming.getAudioOutputTokens());
        if (incoming.getWebSearchRequests() != null) usage.setWebSearchRequests(incoming.getWebSearchRequests());
        if (incoming.getServiceTier() != null) usage.setServiceTier(incoming.getServiceTier());
        if (incoming.getRawExtra() != null) usage.setRawExtra(incoming.getRawExtra());

        if (usage.getTotalTokens() == null
                && usage.getInputTokens() != null
                && usage.getOutputTokens() != null) {
            usage.setTotalTokens(usage.getInputTokens() + usage.getOutputTokens());
        }
        if (usage.getCachedInputTokens() == null
                && (usage.getCacheCreationInputTokens() != null || usage.getCacheReadInputTokens() != null)) {
            usage.setCachedInputTokens(
                    (usage.getCacheCreationInputTokens() != null ? usage.getCacheCreationInputTokens() : 0)
                    + (usage.getCacheReadInputTokens() != null ? usage.getCacheReadInputTokens() : 0));
        }
    }

    /**
     * Clears all state.
     */
    public void clear() {
        responseId = null;
        model = null;
        created = 0;
        toolCalls.clear();
        contentBlocks.clear();
        usage = null;
        stopReason = null;
        contentEmitted = false;
    }
}
