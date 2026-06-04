package io.github.kongweiguang.llmbridge.core.http;

import io.github.kongweiguang.llmbridge.core.config.ModelConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

/**
 * Represents a request to an upstream LLM provider.
 * Contains the target URL, headers, and request body.
 */
@Data
public class UpstreamRequest {

    /** The full URL to send the request to. */
    private String url;

    /** The model configuration for this request. */
    private ModelConfig modelConfig;

    /** The request body (already converted to the target protocol format). */
    private ObjectNode body;

    public UpstreamRequest(String url, ModelConfig modelConfig, ObjectNode body) {
        this.url = url;
        this.modelConfig = modelConfig;
        this.body = body;
    }
}
