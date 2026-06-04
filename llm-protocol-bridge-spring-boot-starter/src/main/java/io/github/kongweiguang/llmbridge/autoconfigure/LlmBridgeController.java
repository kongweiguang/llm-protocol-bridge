package io.github.kongweiguang.llmbridge.autoconfigure;

import io.github.kongweiguang.llmbridge.core.config.LlmBridgeProperties;
import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.error.ErrorMapper;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.json.JacksonUtil;
import io.github.kongweiguang.llmbridge.core.stream.SseFrame;
import io.github.kongweiguang.llmbridge.core.stream.SseFrameWriter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * REST controller that exposes the LLM bridge endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class LlmBridgeController {

    private final LlmBridgeService service;
    private final LlmBridgeProperties properties;
    private final SseFrameWriter sseFormatter;

    public LlmBridgeController(LlmBridgeService service, LlmBridgeProperties properties) {
        this.service = service;
        this.properties = properties;
        this.sseFormatter = new SseFrameWriter();
    }

    /**
     * OpenAI Chat Completions endpoint.
     */
    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> chat(
            @RequestBody Mono<JsonNode> bodyMono,
            ServerHttpResponse response) {
        return handle(ApiProtocol.OPENAI_CHAT_COMPLETIONS, bodyMono, response);
    }

    /**
     * OpenAI Responses endpoint.
     */
    @PostMapping(value = "/responses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> responses(
            @RequestBody Mono<JsonNode> bodyMono,
            ServerHttpResponse response) {
        return handle(ApiProtocol.OPENAI_RESPONSES, bodyMono, response);
    }

    /**
     * Anthropic Messages endpoint.
     */
    @PostMapping(value = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> messages(
            @RequestBody Mono<JsonNode> bodyMono,
            ServerHttpResponse response) {
        return handle(ApiProtocol.ANTHROPIC_MESSAGES, bodyMono, response);
    }

    /**
     * Models listing endpoint.
     */
    @GetMapping("/models")
    public Mono<ResponseEntity<JsonNode>> models() {
        return Mono.just(ResponseEntity.ok(service.listModels()));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        int modelCount = (properties.getModelAliases() != null ? properties.getModelAliases().size() : 0)
                + (properties.getRoutes() != null ? properties.getRoutes().size() : 0);
        return Mono.just(Map.of("status", "ok", "models", modelCount));
    }

    /**
     * Handles both streaming and non-streaming requests.
     */
    private Mono<Void> handle(ApiProtocol sourceFormat, Mono<JsonNode> bodyMono, ServerHttpResponse response) {
        return bodyMono.flatMap(body -> {
            boolean stream = body.path("stream").asBoolean(false);
            String model = JacksonUtil.getString(body, "model");
            log.info("incoming request: format={}, model={}, stream={}", sourceFormat, model, stream);

            if (!stream) {
                return service.proxy(sourceFormat, body)
                        .flatMap(json -> {
                            log.info("request completed: format={}, model={}", sourceFormat, model);
                            response.setStatusCode(HttpStatus.OK);
                            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
                            ByteBuffer buffer = ByteBuffer.wrap(bytes);
                            return response.writeWith(Mono.just(
                                    response.bufferFactory().wrap(buffer)));
                        });
            }

            // Streaming: return SSE response
            response.setStatusCode(HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
            response.getHeaders().setCacheControl(CacheControl.noCache());
            response.getHeaders().add("X-Accel-Buffering", "no");

            log.info("streaming request started: format={}, model={}", sourceFormat, model);
            Flux<SseFrame> sseEvents = service.proxyStream(sourceFormat, body);

            DataBufferFactory bufferFactory = response.bufferFactory();
            Flux<DataBuffer> data = sseFormatter.formatToString(sseEvents)
                    .map(s -> {
                        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                        return bufferFactory.wrap(bytes);
                    });

            return response.writeWith(data);
        }).onErrorResume(e -> {
            BridgeException be;
            if (e instanceof BridgeException b) {
                be = b;
            } else {
                be = new BridgeException(500, "internal_error", e.getMessage(), e);
            }

            log.error("request failed: format={}, error={}", sourceFormat, be.getMessage(), be);

            if (response.isCommitted()) {
                return Mono.error(be);
            }

            response.setStatusCode(HttpStatus.valueOf(be.getError().getStatus()));
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] bytes = ErrorMapper.toOpenAiFormat(be.getError())
                    .toString().getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(buffer)));
        });
    }
}
