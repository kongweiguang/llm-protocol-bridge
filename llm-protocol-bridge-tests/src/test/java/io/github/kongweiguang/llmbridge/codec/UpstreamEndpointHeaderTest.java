package io.github.kongweiguang.llmbridge.codec;

import io.github.kongweiguang.llmbridge.core.config.ModelConfig;
import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import io.github.kongweiguang.llmbridge.core.http.UpstreamEndpointResolver;
import io.github.kongweiguang.llmbridge.core.http.UpstreamHeaderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for UpstreamEndpointResolver and UpstreamHeaderFactory.
 */
class UpstreamEndpointHeaderTest {

    private final UpstreamEndpointResolver endpointResolver = new UpstreamEndpointResolver();
    private final UpstreamHeaderFactory headerFactory = new UpstreamHeaderFactory();

    private ModelConfig createConfig(ProviderKind provider, String baseUrl) {
        ModelConfig mc = new ModelConfig();
        mc.setName("test");
        mc.setProvider(provider);
        mc.setBaseUrl(baseUrl);
        mc.setApiKey("sk-test");
        mc.setModel("model-1");
        return mc;
    }

    // ===== Endpoint Resolution =====

    @Test
    void openAiChat_withV1Suffix() {
        ModelConfig config = createConfig(ProviderKind.OPENAI_CHAT_COMPATIBLE, "https://api.openai.com/v1");
        String url = endpointResolver.resolveUrl(config);
        assertThat(url).isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    void openAiChat_withoutV1Suffix() {
        ModelConfig config = createConfig(ProviderKind.OPENAI_CHAT_COMPATIBLE, "https://api.openai.com");
        String url = endpointResolver.resolveUrl(config);
        assertThat(url).isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    void openAiResponses_withV1Suffix() {
        ModelConfig config = createConfig(ProviderKind.OPENAI_RESPONSES_COMPATIBLE, "https://api.openai.com/v1");
        String url = endpointResolver.resolveUrl(config);
        assertThat(url).isEqualTo("https://api.openai.com/v1/responses");
    }

    @Test
    void anthropic_withV1Suffix() {
        ModelConfig config = createConfig(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE, "https://api.anthropic.com/v1");
        String url = endpointResolver.resolveUrl(config);
        assertThat(url).isEqualTo("https://api.anthropic.com/v1/messages");
    }

    @Test
    void anthropic_withoutV1Suffix() {
        ModelConfig config = createConfig(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE, "https://api.anthropic.com");
        String url = endpointResolver.resolveUrl(config);
        assertThat(url).isEqualTo("https://api.anthropic.com/v1/messages");
    }

    @Test
    void trailingSlash_isHandled() {
        ModelConfig config = createConfig(ProviderKind.OPENAI_CHAT_COMPATIBLE, "https://api.openai.com/v1/");
        String url = endpointResolver.resolveUrl(config);
        assertThat(url).isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    void missingBaseUrl_throws() {
        ModelConfig config = createConfig(ProviderKind.OPENAI_CHAT_COMPATIBLE, null);
        assertThatThrownBy(() -> endpointResolver.resolveUrl(config))
                .isInstanceOf(BridgeException.class)
                .hasMessageContaining("base-url is required");
    }

    @Test
    void missingProvider_throws() {
        ModelConfig config = new ModelConfig();
        config.setName("test");
        config.setBaseUrl("https://example.com");
        assertThatThrownBy(() -> endpointResolver.resolveUrl(config))
                .isInstanceOf(BridgeException.class)
                .hasMessageContaining("provider is required");
    }

    // ===== Header Construction =====

    @Test
    void openAi_usesBearerAuth() {
        ModelConfig config = createConfig(ProviderKind.OPENAI_CHAT_COMPATIBLE, "https://api.openai.com/v1");
        HttpHeaders headers = headerFactory.buildHeaders(config);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-test");
        assertThat(headers.getFirst("x-api-key")).isNull();
        assertThat(headers.getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
    }

    @Test
    void anthropic_usesXApiKey() {
        ModelConfig config = createConfig(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE, "https://api.anthropic.com/v1");
        HttpHeaders headers = headerFactory.buildHeaders(config);

        assertThat(headers.getFirst("x-api-key")).isEqualTo("sk-test");
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(headers.getFirst("anthropic-version")).isEqualTo("2023-06-01");
    }

    @Test
    void anthropic_customVersion() {
        ModelConfig config = createConfig(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE, "https://api.anthropic.com/v1");
        config.setAnthropicVersion("2024-01-01");
        HttpHeaders headers = headerFactory.buildHeaders(config);

        assertThat(headers.getFirst("anthropic-version")).isEqualTo("2024-01-01");
    }

    @Test
    void customHeaders_overrideDefaults() {
        ModelConfig config = createConfig(ProviderKind.OPENAI_CHAT_COMPATIBLE, "https://api.openai.com/v1");
        config.setHeaders(java.util.Map.of("user-agent", "my-app", "x-custom", "value"));
        HttpHeaders headers = headerFactory.buildHeaders(config);

        assertThat(headers.getFirst("user-agent")).isEqualTo("my-app");
        assertThat(headers.getFirst("x-custom")).isEqualTo("value");
    }
}
