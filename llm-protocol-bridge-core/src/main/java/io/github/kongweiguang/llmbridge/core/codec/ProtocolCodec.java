package io.github.kongweiguang.llmbridge.core.codec;

import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalRequest;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalResponse;
import io.github.kongweiguang.llmbridge.core.stream.CanonicalStreamEvent;
import io.github.kongweiguang.llmbridge.core.stream.SseFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Flux;

/**
 * Core interface for protocol encoding/decoding.
 * Each implementation handles one API format (OpenAI Chat, OpenAI Responses, Anthropic Messages).
 *
 * <p>The codec converts between raw protocol JSON and the unified normalized model.
 * It does NOT handle HTTP concerns — that is the responsibility of the upstream client.</p>
 */
public interface ProtocolCodec {

    /**
     * Returns the API protocol this codec handles.
     *
     * @return the API protocol
     */
    ApiProtocol apiProtocol();

    /**
     * Converts a raw protocol request to the normalized request model.
     *
     * @param rawRequest the raw JSON request from the client
     * @param context    the bridge context for this request
     * @return the normalized request
     */
    CanonicalRequest normalizeRequest(JsonNode rawRequest, BridgeContext context);

    /**
     * Converts a normalized request to the raw protocol format for the upstream.
     *
     * @param request the normalized request
     * @param context the bridge context
     * @return the raw JSON request for the upstream
     */
    ObjectNode denormalizeRequest(CanonicalRequest request, BridgeContext context);

    /**
     * Converts a raw protocol response from the upstream to the normalized response model.
     *
     * @param rawResponse the raw JSON response from the upstream
     * @param context     the bridge context
     * @return the normalized response
     */
    CanonicalResponse normalizeResponse(JsonNode rawResponse, BridgeContext context);

    /**
     * Converts a normalized response to the raw protocol format for the client.
     *
     * @param response the normalized response
     * @param context  the bridge context
     * @return the raw JSON response for the client
     */
    ObjectNode denormalizeResponse(CanonicalResponse response, BridgeContext context);

    /**
     * Converts a raw SSE stream from the upstream to normalized stream events.
     * Default implementation throws UnsupportedOperationException.
     *
     * @param rawEvents the raw SSE events from the upstream
     * @param context   the bridge context
     * @return a flux of normalized stream events
     */
    default Flux<CanonicalStreamEvent> normalizeStream(
            Flux<SseFrame> rawEvents,
            BridgeContext context
    ) {
        throw new UnsupportedOperationException("stream not implemented for " + apiProtocol());
    }

    /**
     * Converts normalized stream events to the raw SSE format for the client.
     * Default implementation throws UnsupportedOperationException.
     *
     * @param events  the normalized stream events
     * @param context the bridge context
     * @return a flux of raw SSE events
     */
    default Flux<SseFrame> denormalizeStream(
            Flux<CanonicalStreamEvent> events,
            BridgeContext context
    ) {
        throw new UnsupportedOperationException("stream not implemented for " + apiProtocol());
    }

    /**
     * Context object carrying request-scoped information through the codec pipeline.
     *
     * @param sourceFormat the API format of the incoming client request
     * @param targetFormat the API format of the upstream provider
     * @param requestedModel the model name requested by the client
     */
    record BridgeContext(
            ApiProtocol sourceFormat,
            ApiProtocol targetFormat,
            String requestedModel
    ) {
    }
}
