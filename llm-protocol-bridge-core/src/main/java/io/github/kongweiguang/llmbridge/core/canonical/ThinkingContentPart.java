package io.github.kongweiguang.llmbridge.core.canonical;

import lombok.Data;

/**
 * A thinking/reasoning content part in a normalized message.
 * Represents internal reasoning from models that support it (e.g., Anthropic extended thinking).
 */
@Data
public class ThinkingContentPart implements CanonicalContentPart {

    /** The thinking text content. */
    private String thinking;

    /** Cryptographic signature for the thinking block (Anthropic). */
    private String signature;

    @Override
    public String type() {
        return "thinking";
    }
}
