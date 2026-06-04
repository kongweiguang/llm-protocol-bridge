package io.github.kongweiguang.llmbridge.core.http;

import io.github.kongweiguang.llmbridge.core.config.ModelConfig;
import io.github.kongweiguang.llmbridge.core.stream.SseFrame;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface for sending requests to upstream LLM providers.
 * Implementations handle HTTP communication, header construction, and URL resolution.
 */
public interface UpstreamHttpClient {

    /**
     * Sends a non-streaming request to the upstream provider.
     *
     * @param request the upstream request
     * @return a Mono emitting the upstream response
     */
    Mono<UpstreamResponse> post(UpstreamRequest request);

    /**
     * Sends a streaming request to the upstream provider.
     * Returns a flux of SSE events from the upstream.
     *
     * @param modelConfig the model configuration
     * @param body        the request body (with stream=true)
     * @return a flux of SSE events
     */
    Flux<SseFrame> postJsonStream(ModelConfig modelConfig, ObjectNode body);
}
