package io.github.kongweiguang.llmbridge.core.http;

import io.github.kongweiguang.llmbridge.core.config.ModelConfig;
import io.github.kongweiguang.llmbridge.core.config.ServerConfig;
import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.stream.SseFrame;
import io.github.kongweiguang.llmbridge.core.stream.SseFrameParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

/**
 * WebClient-based implementation of {@link UpstreamHttpClient}.
 * Handles HTTP communication with upstream LLM providers using Spring WebFlux WebClient.
 */
@Slf4j
public class WebClientUpstreamHttpClient implements UpstreamHttpClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ServerConfig serverConfig;
    private final SseFrameParser sseParser;
    private final UpstreamEndpointResolver endpointResolver;
    private final UpstreamHeaderFactory headerFactory;

    public WebClientUpstreamHttpClient(WebClient webClient, ObjectMapper objectMapper, ServerConfig serverConfig) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.serverConfig = serverConfig;
        this.sseParser = new SseFrameParser();
        this.endpointResolver = new UpstreamEndpointResolver();
        this.headerFactory = new UpstreamHeaderFactory();
    }

    @Override
    public Mono<UpstreamResponse> post(UpstreamRequest request) {
        ModelConfig config = request.getModelConfig();
        String url = endpointResolver.resolveUrl(config);
        HttpHeaders headers = headerFactory.buildHeaders(config);

        log.info("upstream POST: url={}, model={}", url, config.getName());

        return webClient.post()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request.getBody().toString())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(serverConfig.getRequestTimeout())
                .map(body -> {
                    try {
                        JsonNode jsonBody = objectMapper.readTree(body);
                        log.debug("upstream response received: model={}", config.getName());
                        return new UpstreamResponse(200, jsonBody);
                    } catch (Exception e) {
                        throw new BridgeException(502, "upstream_error",
                                "Failed to parse upstream response: " + e.getMessage(), e);
                    }
                })
                .onErrorMap(e -> {
                    if (e instanceof BridgeException) return e;
                    log.error("upstream request failed: url={}, model={}, error={}", url, config.getName(), e.getMessage());
                    return new BridgeException(502, "upstream_error",
                            "Upstream request failed: " + e.getMessage(), e);
                });
    }

    @Override
    public Flux<SseFrame> postJsonStream(ModelConfig modelConfig, ObjectNode body) {
        String url = endpointResolver.resolveUrl(modelConfig);
        HttpHeaders headers = headerFactory.buildHeaders(modelConfig, true);

        log.info("upstream stream POST: url={}, model={}", url, modelConfig.getName());

        SseFrameParser parser = new SseFrameParser();

        return webClient.post()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(serverConfig.getRequestTimeout())
                .map(dataBuffer -> {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(
                            new byte[dataBuffer.readableByteCount()]);
                    dataBuffer.read(byteBuffer.array());
                    return byteBuffer;
                })
                .transform(flux -> parser.parse(flux))
                .onErrorMap(e -> {
                    if (e instanceof BridgeException) return e;
                    log.error("upstream stream request failed: url={}, model={}, error={}", url, modelConfig.getName(), e.getMessage());
                    return new BridgeException(502, "upstream_error",
                            "Upstream stream request failed: " + e.getMessage(), e);
                })
                .doOnCancel(() -> log.debug("Client cancelled stream for model: {}", modelConfig.getName()));
    }
}
