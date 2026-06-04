package io.github.kongweiguang.llmbridge.autoconfigure;

import io.github.kongweiguang.llmbridge.core.http.UpstreamHttpClient;
import io.github.kongweiguang.llmbridge.core.http.UpstreamRequest;
import io.github.kongweiguang.llmbridge.core.http.UpstreamResponse;
import io.github.kongweiguang.llmbridge.core.codec.ProtocolCodecRegistry;
import io.github.kongweiguang.llmbridge.core.codec.ProtocolCodec;
import io.github.kongweiguang.llmbridge.core.config.LlmBridgeProperties;
import io.github.kongweiguang.llmbridge.core.config.ModelConfig;
import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import io.github.kongweiguang.llmbridge.core.json.JacksonUtil;
import io.github.kongweiguang.llmbridge.core.json.JsonMerge;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalRequest;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalResponse;
import io.github.kongweiguang.llmbridge.core.routing.ModelResolver;
import io.github.kongweiguang.llmbridge.core.routing.ModelResolutionResult;
import io.github.kongweiguang.llmbridge.core.stream.SseFrame;
import io.github.kongweiguang.llmbridge.core.stream.SseFrameParser;
import io.github.kongweiguang.llmbridge.core.stream.StreamErrorMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Core service that handles the proxy logic for the LLM bridge.
 * Routes requests, converts between protocols, and handles fallback.
 */
@Slf4j
public class LlmBridgeService {

    private final ProtocolCodecRegistry codecRegistry;
    private final ModelResolver modelRouter;
    private final UpstreamHttpClient upstreamClient;
    private final LlmBridgeProperties properties;

    public LlmBridgeService(ProtocolCodecRegistry codecRegistry, ModelResolver modelRouter,
                            UpstreamHttpClient upstreamClient, LlmBridgeProperties properties) {
        this.codecRegistry = codecRegistry;
        this.modelRouter = modelRouter;
        this.upstreamClient = upstreamClient;
        this.properties = properties;
    }

    /**
     * Proxies a non-streaming request from the client to the upstream LLM provider.
     */
    public Mono<JsonNode> proxy(ApiProtocol sourceFormat, JsonNode clientBody) {
        String requestedModel = extractModel(clientBody, sourceFormat);
        log.info("proxy request: model={}, format={}", requestedModel, sourceFormat);

        ModelResolutionResult route = modelRouter.resolve(requestedModel);

        return callWithFallback(sourceFormat, clientBody, route, 0);
    }

    /**
     * Proxies a streaming request from the client to the upstream LLM provider.
     */
    public Flux<SseFrame> proxyStream(ApiProtocol sourceFormat, JsonNode clientBody) {
        if (!properties.getStream().isEnabled()) {
            return Flux.error(new BridgeException(400, "invalid_request_error",
                    "Streaming is not enabled"));
        }

        String requestedModel = extractModel(clientBody, sourceFormat);
        log.info("proxy stream request: model={}, format={}", requestedModel, sourceFormat);

        ModelResolutionResult route = modelRouter.resolve(requestedModel);

        // For streaming, we only try the first candidate (no fallback during stream)
        ModelConfig candidate = route.getModelConfig();
        return callCandidateStream(sourceFormat, clientBody, candidate, requestedModel);
    }

    private Flux<SseFrame> callCandidateStream(ApiProtocol sourceFormat, JsonNode clientBody,
                                                  ModelConfig candidate, String requestedModel) {
        try {
            ProtocolCodec sourceCodec = codecRegistry.get(sourceFormat);
            ApiProtocol targetFormat = candidate.getProvider().apiProtocol();
            ProtocolCodec targetCodec = codecRegistry.get(targetFormat);

            ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                    sourceFormat, targetFormat, requestedModel);

            log.info("stream pipeline: source={}, target={}, upstreamModel={}, candidate={}",
                    sourceFormat, targetFormat, candidate.getModel(), candidate.getName());

            // 1. Normalize request
            CanonicalRequest normalizedRequest = sourceCodec.normalizeRequest(clientBody, context);

            // 2. Override model with the actual upstream model
            normalizedRequest.setModel(candidate.getModel());

            // 3. Force stream=true
            normalizedRequest.setStream(true);

            // 4. Denormalize to target format
            ObjectNode upstreamBody = targetCodec.denormalizeRequest(normalizedRequest, context);

            // 5. Ensure stream=true in upstream body
            upstreamBody.put("stream", true);

            // 6. For OpenAI Chat, add stream_options.include_usage if configured
            if (targetFormat == ApiProtocol.OPENAI_CHAT_COMPLETIONS && properties.getStream().isIncludeUsage()) {
                ObjectNode streamOptions = upstreamBody.putObject("stream_options");
                streamOptions.put("include_usage", true);
            }

            // 7. Deep merge with configured body overrides
            if (candidate.getBody() != null) {
                upstreamBody = JsonMerge.deepMerge(upstreamBody, candidate.getBody());
            }

            // 7a. Apply request-defaults (fill missing fields only)
            if (candidate.getRequestDefaults() != null) {
                JacksonUtil.applyMissing(upstreamBody, candidate.getRequestDefaults());
            }

            // 7b. Apply request-overrides (force overwrite)
            if (candidate.getRequestOverrides() != null) {
                upstreamBody = JsonMerge.deepMerge(upstreamBody, candidate.getRequestOverrides());
            }

            // 8. Send streaming request to upstream
            log.debug("sending stream request to upstream for model={}", candidate.getName());
            Flux<SseFrame> upstreamEvents = upstreamClient.postJsonStream(candidate, upstreamBody);

            // 9. Normalize upstream stream events
            Flux<io.github.kongweiguang.llmbridge.core.stream.CanonicalStreamEvent> normalizedEvents =
                    targetCodec.normalizeStream(upstreamEvents, context);

            // 10. Denormalize to client format
            Flux<SseFrame> clientEvents = sourceCodec.denormalizeStream(normalizedEvents, context);

            // 11. Handle errors by converting to client protocol error events
            return clientEvents
                    .onErrorResume(e -> {
                        BridgeException be;
                        if (e instanceof BridgeException b) {
                            be = b;
                        } else {
                            be = new BridgeException(502, "upstream_error",
                                    "Stream error: " + e.getMessage(), e);
                        }
                        log.error("stream error for model={}: {}", candidate.getName(), be.getMessage(), be);
                        return Flux.just(
                                StreamErrorMapper.toErrorEvent(be, sourceFormat),
                                StreamErrorMapper.doneEvent()
                        );
                    })
                    .doOnCancel(() -> log.debug("Client cancelled stream for model: {}", candidate.getName()));

        } catch (Exception e) {
            BridgeException be;
            if (e instanceof BridgeException b) {
                be = b;
            } else {
                be = new BridgeException(502, "upstream_error",
                        "Failed to proxy stream request: " + e.getMessage(), e);
            }
            log.error("stream proxy failed for model={}: {}", candidate.getName(), be.getMessage(), be);
            return Flux.just(
                    StreamErrorMapper.toErrorEvent(be, sourceFormat),
                    StreamErrorMapper.doneEvent()
            );
        }
    }

    private Mono<JsonNode> callWithFallback(ApiProtocol sourceFormat, JsonNode clientBody,
                                              ModelResolutionResult route, int candidateIndex) {
        if (candidateIndex >= route.getFallbackCandidates().size()) {
            log.error("all fallback candidates exhausted for model={}", route.getRequestedModel());
            return Mono.error(new BridgeException(502, "upstream_error",
                    "All fallback candidates failed for model: " + route.getRequestedModel()));
        }

        ModelConfig candidate = route.getFallbackCandidates().get(candidateIndex);
        log.info("trying candidate: name={}, model={}, index={}/{}", candidate.getName(), candidate.getModel(),
                candidateIndex + 1, route.getFallbackCandidates().size());

        return callCandidate(sourceFormat, clientBody, candidate, route.getRequestedModel())
                .onErrorResume(e -> {
                    log.warn("candidate {} failed: {}", candidate.getName(), e.getMessage());
                    return callWithFallback(sourceFormat, clientBody, route, candidateIndex + 1);
                });
    }

    private Mono<JsonNode> callCandidate(ApiProtocol sourceFormat, JsonNode clientBody,
                                           ModelConfig candidate, String requestedModel) {
        try {
            ProtocolCodec sourceCodec = codecRegistry.get(sourceFormat);
            ApiProtocol targetFormat = candidate.getProvider().apiProtocol();
            ProtocolCodec targetCodec = codecRegistry.get(targetFormat);

            ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                    sourceFormat, targetFormat, requestedModel);

            log.debug("codec pipeline: source={}, target={}, upstreamModel={}",
                    sourceFormat, targetFormat, candidate.getModel());

            // 1. Normalize request
            CanonicalRequest normalizedRequest = sourceCodec.normalizeRequest(clientBody, context);

            // 2. Override model with the actual upstream model
            normalizedRequest.setModel(candidate.getModel());

            // 3. Denormalize to target format
            ObjectNode upstreamBody = targetCodec.denormalizeRequest(normalizedRequest, context);

            // 4. Deep merge with configured body overrides
            if (candidate.getBody() != null) {
                upstreamBody = JsonMerge.deepMerge(upstreamBody, candidate.getBody());
            }

            // 4a. Apply request-defaults (fill missing fields only)
            if (candidate.getRequestDefaults() != null) {
                JacksonUtil.applyMissing(upstreamBody, candidate.getRequestDefaults());
            }

            // 4b. Apply request-overrides (force overwrite)
            if (candidate.getRequestOverrides() != null) {
                upstreamBody = JsonMerge.deepMerge(upstreamBody, candidate.getRequestOverrides());
            }

            // 5. Send to upstream
            UpstreamRequest upstreamRequest = new UpstreamRequest(null, candidate, upstreamBody);
            log.debug("sending request to upstream for model={}", candidate.getName());

            return upstreamClient.post(upstreamRequest)
                    .map(upstreamResponse -> {
                        // 6. Normalize upstream response
                        CanonicalResponse normalizedResponse = targetCodec.normalizeResponse(
                                upstreamResponse.getBody(), context);

                        // 7. Set model fields for proper denormalization
                        normalizedResponse.setRequestedModel(requestedModel);

                        // 8. Denormalize to client format
                        JsonNode result = sourceCodec.denormalizeResponse(normalizedResponse, context);
                        log.info("request completed: model={}, candidate={}", requestedModel, candidate.getName());
                        return result;
                    });
        } catch (Exception e) {
            log.error("proxy call failed for model={}: {}", candidate.getName(), e.getMessage(), e);
            return Mono.error(new BridgeException(502, "upstream_error",
                    "Failed to proxy request: " + e.getMessage(), e));
        }
    }

    /**
     * Extracts the model name from the request body based on the source format.
     */
    private String extractModel(JsonNode body, ApiProtocol format) {
        String model = JacksonUtil.getString(body, "model");
        if (model == null || model.isEmpty()) {
            throw new BridgeException(400, "invalid_request_error", "model is required");
        }
        return model;
    }

    /**
     * Returns the list of configured models for the /v1/models endpoint.
     */
    public JsonNode listModels() {
        ObjectNode root = JacksonUtil.objectNode();
        root.put("object", "list");
        var dataArr = root.putArray("data");

        if (properties.getModelAliases() != null) {
            for (var entry : properties.getModelAliases().entrySet()) {
                ObjectNode modelNode = JacksonUtil.objectNode();
                modelNode.put("id", entry.getKey());
                modelNode.put("object", "model");
                modelNode.put("created", System.currentTimeMillis() / 1000);
                modelNode.put("owned_by", entry.getValue().getProviderRef());
                dataArr.add(modelNode);
            }
        }

        if (properties.getRoutes() != null) {
            for (String routeName : properties.getRoutes().keySet()) {
                ObjectNode modelNode = JacksonUtil.objectNode();
                modelNode.put("id", routeName);
                modelNode.put("object", "model");
                modelNode.put("created", System.currentTimeMillis() / 1000);
                modelNode.put("owned_by", "llm-bridge");
                dataArr.add(modelNode);
            }
        }

        return root;
    }

    /**
     * Returns a health check response.
     */
    public JsonNode health() {
        ObjectNode root = JacksonUtil.objectNode();
        root.put("status", "ok");
        int modelCount = (properties.getModelAliases() != null ? properties.getModelAliases().size() : 0)
                + (properties.getRoutes() != null ? properties.getRoutes().size() : 0);
        root.put("models", modelCount);
        return root;
    }
}
