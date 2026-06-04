package io.github.kongweiguang.llmbridge.core.canonical;

import lombok.Data;

/**
 * An audio content part in a normalized message.
 * Supports both URL-based and base64-encoded audio.
 */
@Data
public class AudioContentPart implements CanonicalContentPart {

    /** Audio URL. */
    private String url;

    /** Base64-encoded audio data. */
    private String base64;

    /** MIME media type (e.g., "audio/wav", "audio/mp3"). */
    private String mediaType;

    /** Audio format (e.g., "wav", "mp3"). */
    private String format;

    @Override
    public String type() {
        return "audio";
    }
}
