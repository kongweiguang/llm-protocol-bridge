package io.github.kongweiguang.llmbridge.core.stream;

import io.github.kongweiguang.llmbridge.core.json.JacksonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the state of a single tool call during streaming.
 * Accumulates arguments deltas and attempts JSON parsing on completion.
 */
@Slf4j
@Getter
@Setter
public class ToolCallStateTracker {

    private final int index;
    private String id;
    private String name;
    private final StringBuilder argumentsBuilder = new StringBuilder();
    private ObjectNode parsedArguments;

    /**
     * Creates a new ToolCallStateTracker for the specified index.
     *
     * @param index the tool call index
     */
    public ToolCallStateTracker(int index) {
        this.index = index;
    }

    /**
     * Creates a new ToolCallStateTracker with initial values.
     *
     * @param index the tool call index
     * @param id    the tool call ID
     * @param name  the tool name
     */
    public ToolCallStateTracker(int index, String id, String name) {
        this.index = index;
        this.id = id;
        this.name = name;
    }

    /**
     * Appends an arguments delta to the accumulated arguments.
     *
     * @param delta the arguments delta string
     */
    public void appendArguments(String delta) {
        if (delta != null) {
            argumentsBuilder.append(delta);
        }
    }

    /**
     * Attempts to parse the accumulated arguments as JSON.
     * If parsing fails, parsedArguments will be null.
     */
    public void parseArguments() {
        String raw = argumentsBuilder.toString();
        if (raw.isEmpty()) {
            parsedArguments = JacksonUtil.objectNode();
            return;
        }
        JsonNode parsed = JacksonUtil.tryParse(raw);
        if (parsed != null && parsed.isObject()) {
            parsedArguments = (ObjectNode) parsed;
        } else {
            log.debug("Tool arguments not valid JSON, keeping raw string: {}", raw);
            parsedArguments = null;
        }
    }

    public String getArgumentsString() {
        return argumentsBuilder.toString();
    }

    public boolean isParsed() {
        return parsedArguments != null;
    }
}
