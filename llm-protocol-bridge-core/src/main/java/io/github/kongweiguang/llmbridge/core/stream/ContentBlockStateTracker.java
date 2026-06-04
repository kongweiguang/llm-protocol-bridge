package io.github.kongweiguang.llmbridge.core.stream;

import lombok.Getter;
import lombok.Setter;

/**
 * Tracks the state of a content block during Anthropic-style streaming.
 * Accumulates text, tool input JSON, and thinking content.
 */
@Getter
@Setter
public class ContentBlockStateTracker {

    private final int index;
    private String type;
    private String id;
    private String name;
    private final StringBuilder textBuilder = new StringBuilder();
    private final StringBuilder inputJsonBuilder = new StringBuilder();
    private final StringBuilder thinkingBuilder = new StringBuilder();
    private String signature;

    /**
     * Creates a new ContentBlockStateTracker for the specified index.
     *
     * @param index the content block index
     */
    public ContentBlockStateTracker(int index) {
        this.index = index;
    }

    /**
     * Creates a new ContentBlockStateTracker with initial values.
     *
     * @param index the content block index
     * @param type  the content block type (text, tool_use, thinking)
     */
    public ContentBlockStateTracker(int index, String type) {
        this.index = index;
        this.type = type;
    }

    /**
     * Appends text delta to the accumulated text.
     *
     * @param delta the text delta
     */
    public void appendText(String delta) {
        if (delta != null) {
            textBuilder.append(delta);
        }
    }

    /**
     * Appends input JSON delta to the accumulated input JSON.
     *
     * @param delta the input JSON delta
     */
    public void appendInputJson(String delta) {
        if (delta != null) {
            inputJsonBuilder.append(delta);
        }
    }

    /**
     * Appends thinking delta to the accumulated thinking content.
     *
     * @param delta the thinking delta
     */
    public void appendThinking(String delta) {
        if (delta != null) {
            thinkingBuilder.append(delta);
        }
    }

    public String getText() {
        return textBuilder.toString();
    }

    public String getInputJson() {
        return inputJsonBuilder.toString();
    }

    public String getThinking() {
        return thinkingBuilder.toString();
    }
}
