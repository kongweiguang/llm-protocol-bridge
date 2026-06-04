package io.github.kongweiguang.llmbridge.codec;

import io.github.kongweiguang.llmbridge.core.codec.AnthropicMessagesCodec;
import io.github.kongweiguang.llmbridge.core.codec.ProtocolCodec;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.canonical.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnthropicMessagesCodec}.
 */
class AnthropicMessagesCodecTest {

    private AnthropicMessagesCodec codec;
    private ObjectMapper mapper;
    private ProtocolCodec.BridgeContext context;

    @BeforeEach
    void setUp() {
        codec = new AnthropicMessagesCodec();
        mapper = new ObjectMapper();
        context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "test-model");
    }

    @Test
    void apiProtocol() {
        assertThat(codec.apiProtocol()).isEqualTo(ApiProtocol.ANTHROPIC_MESSAGES);
    }

    @Test
    void normalizeTextRequest() throws IOException {
        JsonNode request = loadFixture("fixtures/anthropic/text-request.json");
        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        assertThat(normalized.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(normalized.getTemperature()).isEqualTo(0.7);
        assertThat(normalized.getMaxOutputTokens()).isEqualTo(1024);
        assertThat(normalized.getStream()).isFalse();

        // Should have system message from system field + user message
        assertThat(normalized.getMessages()).hasSize(2);
        assertThat(normalized.getMessages().get(0).getRole()).isEqualTo(CanonicalRole.SYSTEM);
        assertThat(extractText(normalized.getMessages().get(0))).isEqualTo("You are a helpful assistant.");
        assertThat(normalized.getMessages().get(1).getRole()).isEqualTo(CanonicalRole.USER);
        assertThat(extractText(normalized.getMessages().get(1))).isEqualTo("Hello, how are you?");
    }

    @Test
    void normalizeImageRequest() throws IOException {
        JsonNode request = loadFixture("fixtures/anthropic/image-request.json");
        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        assertThat(normalized.getMessages()).hasSize(1);
        CanonicalMessage msg = normalized.getMessages().get(0);
        assertThat(msg.getRole()).isEqualTo(CanonicalRole.USER);
        assertThat(msg.getContent()).hasSize(2);
        assertThat(msg.getContent().get(0)).isInstanceOf(TextContentPart.class);
        assertThat(msg.getContent().get(1)).isInstanceOf(ImageContentPart.class);

        ImageContentPart img = (ImageContentPart) msg.getContent().get(1);
        assertThat(img.getUrl()).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    void normalizeToolRequest() throws IOException {
        JsonNode request = loadFixture("fixtures/anthropic/tool-request.json");
        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        assertThat(normalized.getTools()).hasSize(1);
        assertThat(normalized.getTools().get(0).getName()).isEqualTo("get_weather");
        assertThat(normalized.getTools().get(0).getDescription()).isNotEmpty();
    }

    @Test
    void normalizeResponse() throws IOException {
        JsonNode response = loadFixture("fixtures/anthropic/response.json");
        CanonicalResponse normalized = codec.normalizeResponse(response, context);

        assertThat(normalized.getId()).isEqualTo("msg-123");
        assertThat(normalized.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(normalized.getStopReason()).isEqualTo("end_turn");
        assertThat(normalized.getOutputMessages()).hasSize(1);
        assertThat(extractText(normalized.getOutputMessages().get(0)))
                .isEqualTo("Hello! I'm doing well, thank you for asking. How can I help you today?");

        assertThat(normalized.getUsage()).isNotNull();
        assertThat(normalized.getUsage().getInputTokens()).isEqualTo(10);
        assertThat(normalized.getUsage().getOutputTokens()).isEqualTo(20);
        assertThat(normalized.getUsage().getTotalTokens()).isEqualTo(30);
    }

    @Test
    void denormalizeTextRequest() {
        CanonicalRequest request = new CanonicalRequest();
        request.setModel("claude-sonnet-4-6");
        request.setTemperature(0.7);
        request.setMaxOutputTokens(1024);
        request.setStream(false);
        request.setMessages(List.of(
                new CanonicalMessage(CanonicalRole.SYSTEM, List.of(new TextContentPart("You are helpful."))),
                new CanonicalMessage(CanonicalRole.USER, List.of(new TextContentPart("Hello")))
        ));

        ObjectNode denormalized = codec.denormalizeRequest(request, context);

        assertThat(denormalized.get("model").asText()).isEqualTo("claude-sonnet-4-6");
        assertThat(denormalized.get("system").asText()).isEqualTo("You are helpful.");
        assertThat(denormalized.get("messages")).hasSize(1); // only user message
        assertThat(denormalized.get("max_tokens").asInt()).isEqualTo(1024);
    }

    @Test
    void denormalizeResponse() {
        CanonicalResponse response = new CanonicalResponse();
        response.setId("msg-123");
        response.setModel("claude-sonnet-4-6");
        response.setStopReason("end_turn");
        response.setOutputMessages(List.of(
                new CanonicalMessage(CanonicalRole.ASSISTANT, List.of(new TextContentPart("Hello!")))
        ));
        CanonicalUsage usage = new CanonicalUsage();
        usage.setInputTokens(10);
        usage.setOutputTokens(20);
        usage.setTotalTokens(30);
        response.setUsage(usage);

        ObjectNode denormalized = codec.denormalizeResponse(response, context);

        assertThat(denormalized.get("id").asText()).isEqualTo("msg-123");
        assertThat(denormalized.get("type").asText()).isEqualTo("message");
        assertThat(denormalized.get("role").asText()).isEqualTo("assistant");
        assertThat(denormalized.get("stop_reason").asText()).isEqualTo("end_turn");
        assertThat(denormalized.get("content")).hasSize(1);
        assertThat(denormalized.get("usage").get("input_tokens").asInt()).isEqualTo(10);
        assertThat(denormalized.get("usage").get("output_tokens").asInt()).isEqualTo(20);
    }

    @Test
    void roundTripTextRequest() throws IOException {
        JsonNode original = loadFixture("fixtures/anthropic/text-request.json");
        CanonicalRequest normalized = codec.normalizeRequest(original, context);
        ObjectNode denormalized = codec.denormalizeRequest(normalized, context);

        assertThat(denormalized.get("model").asText()).isEqualTo(original.get("model").asText());
        assertThat(denormalized.get("temperature").asDouble()).isEqualTo(original.get("temperature").asDouble());
        assertThat(denormalized.get("system").asText()).isEqualTo(original.get("system").asText());
    }

    private JsonNode loadFixture(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).isNotNull();
            return mapper.readTree(is);
        }
    }

    private String extractText(CanonicalMessage msg) {
        if (msg.getContent() == null) return null;
        StringBuilder sb = new StringBuilder();
        for (CanonicalContentPart part : msg.getContent()) {
            if (part instanceof TextContentPart tp) {
                sb.append(tp.getText());
            }
        }
        return sb.toString();
    }
}
