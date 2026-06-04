package io.github.kongweiguang.llmbridge.core.canonical;

import lombok.Data;

/**
 * A file content part in a normalized message.
 * Supports file IDs, URLs, and base64-encoded files.
 */
@Data
public class FileContentPart implements CanonicalContentPart {

    /** File ID (for provider-stored files). */
    private String fileId;

    /** Filename. */
    private String filename;

    /** MIME media type (e.g., "application/pdf"). */
    private String mediaType;

    /** File URL. */
    private String url;

    /** Base64-encoded file data. */
    private String base64;

    @Override
    public String type() {
        return "file";
    }
}
