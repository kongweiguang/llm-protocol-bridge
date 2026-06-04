package io.github.kongweiguang.llmbridge.core.canonical;

/**
 * Marker interface for content parts in a normalized message.
 * Implementations include {@link TextContentPart}, {@link ImageContentPart},
 * {@link ToolCallContentPart}, {@link ToolResultContentPart}, and {@link ThinkingContentPart}.
 */
public interface CanonicalContentPart {

    /**
     * Returns the type discriminator for this content part.
     *
     * @return type string (e.g., "text", "image", "tool_call", "tool_result", "thinking")
     */
    String type();
}
