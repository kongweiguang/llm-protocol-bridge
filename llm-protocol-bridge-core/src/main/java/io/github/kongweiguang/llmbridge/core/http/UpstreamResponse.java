package io.github.kongweiguang.llmbridge.core.http;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

/**
 * Represents a response from an upstream LLM provider.
 * Contains the HTTP status code and response body.
 */
@Data
public class UpstreamResponse {

    /** HTTP status code from the upstream. */
    private int statusCode;

    /** The response body as a JSON node. */
    private JsonNode body;

    /** Raw response headers (optional). */
    private Map<String, String> headers;

    public UpstreamResponse(int statusCode, JsonNode body) {
        this.statusCode = statusCode;
        this.body = body;
    }
}
