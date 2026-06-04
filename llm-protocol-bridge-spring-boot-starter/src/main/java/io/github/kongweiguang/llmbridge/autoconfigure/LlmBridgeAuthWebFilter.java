package io.github.kongweiguang.llmbridge.autoconfigure;

import io.github.kongweiguang.llmbridge.core.config.LlmBridgeProperties;
import io.github.kongweiguang.llmbridge.core.error.ErrorMapper;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalError;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * WebFilter that enforces Bearer token authentication on /v1/** endpoints (except /health).
 */
@Slf4j
public class LlmBridgeAuthWebFilter implements WebFilter {

    private final LlmBridgeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmBridgeAuthWebFilter(LlmBridgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip auth for health endpoint
        if (path.equals("/health") || path.equals("/v1/health")) {
            return chain.filter(exchange);
        }

        // Only enforce auth on /v1/** paths
        if (!path.startsWith("/v1/")) {
            return chain.filter(exchange);
        }

        // Check if auth is configured
        String configuredToken = properties.getServer().getAuth().getToken();
        if (configuredToken == null || configuredToken.isEmpty()) {
            log.debug("auth skipped (no token configured): path={}", path);
            return chain.filter(exchange);
        }

        // Extract Bearer token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("auth failed: path={}, reason=missing or invalid Authorization header", path);
            return writeUnauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!configuredToken.equals(token)) {
            log.warn("auth failed: path={}, reason=invalid token", path);
            return writeUnauthorized(exchange, "Invalid token");
        }

        log.debug("auth passed: path={}", path);
        return chain.filter(exchange);
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        CanonicalError error = new CanonicalError(401, "authentication_error", message);
        byte[] bytes = ErrorMapper.toOpenAiFormat(error).toString().getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
