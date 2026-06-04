package io.github.kongweiguang.llmbridge.core.http;

import io.github.kongweiguang.llmbridge.core.config.ModelConfig;
import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the full upstream URL for a given model configuration.
 * Handles provider-specific path construction and base URL normalization.
 */
@Slf4j
public class UpstreamEndpointResolver {

    /**
     * Resolves the full URL for an upstream request based on provider type and base URL.
     *
     * @param config the model configuration
     * @return the resolved URL
     * @throws BridgeException if base-url or provider is not configured
     */
    public String resolveUrl(ModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new BridgeException(500, "config_error", "base-url is required for model: " + config.getName());
        }

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        ProviderKind provider = config.getProvider();
        if (provider == null) {
            throw new BridgeException(500, "config_error", "provider is required for model: " + config.getName());
        }

        boolean hasV1 = baseUrl.endsWith("/v1");

        String url = switch (provider) {
            case OPENAI_CHAT_COMPATIBLE -> hasV1 ? baseUrl + "/chat/completions" : baseUrl + "/v1/chat/completions";
            case OPENAI_RESPONSES_COMPATIBLE -> hasV1 ? baseUrl + "/responses" : baseUrl + "/v1/responses";
            case ANTHROPIC_MESSAGES_COMPATIBLE -> hasV1 ? baseUrl + "/messages" : baseUrl + "/v1/messages";
        };
        log.debug("resolved upstream url: model={}, url={}", config.getName(), url);
        return url;
    }
}
