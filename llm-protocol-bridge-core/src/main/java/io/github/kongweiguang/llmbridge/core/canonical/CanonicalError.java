package io.github.kongweiguang.llmbridge.core.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Unified error model for the bridge.
 * Contains HTTP status, error code, type, message, and optional upstream details.
 */
@Data
public class CanonicalError {

    /** HTTP status code. */
    private int status;

    /** Machine-readable error code. */
    private String code;

    /** Error type/category. */
    private String type;

    /** Human-readable error message. */
    private String message;

    /** The provider that generated the error. */
    private String provider;

    /** The upstream model name. */
    private String upstreamModel;

    /** Raw error response from the upstream. */
    private JsonNode raw;

    public CanonicalError(int status, String type, String message) {
        this.status = status;
        this.type = type;
        this.message = message;
    }
}
