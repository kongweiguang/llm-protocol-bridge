package io.github.kongweiguang.llmbridge.core.format;

/**
 * Enumerates the upstream provider types.
 * Each provider type maps to an {@link ApiProtocol} for protocol conversion.
 */
public enum ProviderKind {

    /** OpenAI Chat Completions compatible provider. */
    OPENAI_CHAT_COMPATIBLE(ApiProtocol.OPENAI_CHAT_COMPLETIONS),

    /** OpenAI Responses compatible provider. */
    OPENAI_RESPONSES_COMPATIBLE(ApiProtocol.OPENAI_RESPONSES),

    /** Anthropic Messages compatible provider. */
    ANTHROPIC_MESSAGES_COMPATIBLE(ApiProtocol.ANTHROPIC_MESSAGES);

    private final ApiProtocol apiProtocol;

    ProviderKind(ApiProtocol apiProtocol) {
        this.apiProtocol = apiProtocol;
    }

    /**
     * Returns the {@link ApiProtocol} associated with this provider kind.
     *
     * @return the API protocol
     */
    public ApiProtocol apiProtocol() {
        return apiProtocol;
    }
}
