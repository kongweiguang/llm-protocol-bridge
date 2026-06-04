package io.github.kongweiguang.llmbridge.core.http;

import io.github.kongweiguang.llmbridge.core.config.ModelConfig;
import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * Constructs HTTP headers for upstream requests based on provider type and model configuration.
 * Handles authentication headers, content type, and custom header overrides.
 */
@Slf4j
public class UpstreamHeaderFactory {

    /**
     * Builds HTTP headers for an upstream request based on provider type and configuration.
     *
     * @param config the model configuration
     * @return the HTTP headers
     */
    public HttpHeaders buildHeaders(ModelConfig config) {
        return buildHeaders(config, false);
    }

    /**
     * Builds HTTP headers for an upstream request.
     *
     * @param config   the model configuration
     * @param streaming whether this is a streaming request
     * @return the HTTP headers
     */
    public HttpHeaders buildHeaders(ModelConfig config, boolean streaming) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT,
                streaming ? MediaType.TEXT_EVENT_STREAM_VALUE : MediaType.APPLICATION_JSON_VALUE);

        ProviderKind provider = config.getProvider();
        String apiKey = config.getApiKey();

        if (provider == ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE) {
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("x-api-key", apiKey);
            }
            String anthropicVersion = config.getAnthropicVersion();
            if (anthropicVersion == null || anthropicVersion.isEmpty()) {
                anthropicVersion = "2023-06-01";
            }
            headers.set("anthropic-version", anthropicVersion);
        } else {
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
        }

        Map<String, String> customHeaders = config.getHeaders();
        if (customHeaders != null) {
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                headers.set(entry.getKey(), entry.getValue());
            }
        }

        log.debug("built headers: provider={}, streaming={}, model={}", provider, streaming, config.getName());
        return headers;
    }
}
