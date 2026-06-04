package io.github.kongweiguang.llmbridge.core.canonical;

/**
 * Unified role enum used in the normalized message model.
 * Maps to the various role representations across OpenAI and Anthropic protocols.
 */
public enum CanonicalRole {

    /** System-level instructions. */
    SYSTEM,

    /** Developer-level instructions (OpenAI Responses). */
    DEVELOPER,

    /** User message. */
    USER,

    /** Assistant/model response. */
    ASSISTANT,

    /** Tool result message. */
    TOOL
}
