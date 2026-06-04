package io.github.kongweiguang.llmbridge.core.format;

/**
 * Enumerates the API protocol formats supported by the bridge.
 * Each value corresponds to a distinct upstream/downstream protocol.
 */
public enum ApiProtocol {

    /** OpenAI Chat Completions API (POST /v1/chat/completions). */
    OPENAI_CHAT_COMPLETIONS,

    /** OpenAI Responses API (POST /v1/responses). */
    OPENAI_RESPONSES,

    /** Anthropic Messages API (POST /v1/messages). */
    ANTHROPIC_MESSAGES
}
