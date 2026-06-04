package io.github.kongweiguang.llmbridge.core.stream;

/**
 * Enumerates the types of normalized stream events.
 * Used to represent stream events from any protocol in a unified way.
 */
public enum CanonicalStreamEventType {

    /** Stream started (response created). */
    START,

    /** Message started (assistant response beginning). */
    MESSAGE_START,

    /** Content block started. */
    CONTENT_BLOCK_START,

    /** Text content delta. */
    TEXT_DELTA,

    /** Refusal content delta. */
    REFUSAL_DELTA,

    /** Thinking/reasoning delta. */
    THINKING_DELTA,

    /** Thinking signature. */
    THINKING_SIGNATURE,

    /** Tool call started. */
    TOOL_CALL_START,

    /** Tool call arguments delta. */
    TOOL_ARGUMENTS_DELTA,

    /** Tool call completed. */
    TOOL_CALL_DONE,

    /** Content block completed. */
    CONTENT_BLOCK_DONE,

    /** Message-level delta (stop reason, usage). */
    MESSAGE_DELTA,

    /** Usage information delta. */
    USAGE_DELTA,

    /** Stream completed. */
    DONE,

    /** Error event. */
    ERROR,

    /** Ping/heartbeat event. */
    PING,

    /** Unknown/unrecognized event. */
    UNKNOWN
}
