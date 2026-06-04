package io.github.kongweiguang.llmbridge.core.canonical;

import lombok.Data;

/**
 * An image content part in a normalized message.
 * Supports both URL-based and base64-encoded images.
 */
@Data
public class ImageContentPart implements CanonicalContentPart {

    /** Image URL (for URL-based images). */
    private String url;

    /** Base64-encoded image data. */
    private String base64;

    /** MIME media type (e.g., "image/png"). */
    private String mediaType;

    /** Image detail level (e.g., "auto", "low", "high"). */
    private String detail;

    @Override
    public String type() {
        return "image";
    }
}
