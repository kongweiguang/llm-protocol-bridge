package io.github.kongweiguang.llmbridge.codec;

import io.github.kongweiguang.llmbridge.core.codec.OpenAiChatCompletionsCodec;
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
 * Unit tests for {@link OpenAiChatCompletionsCodec}.
 */
class OpenAiChatCodecTest {

    private OpenAiChatCompletionsCodec codec;
    private ObjectMapper mapper;
    private ProtocolCodec.BridgeContext context;

    @BeforeEach
    void setUp() {
        codec = new OpenAiChatCompletionsCodec();
        mapper = new ObjectMapper();
        context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "test-model");
    }

    @Test
    void apiProtocol() {
        assertThat(codec.apiProtocol()).isEqualTo(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
    }

    @Test
    void normalizeTextRequest() throws IOException {
        JsonNode request = loadFixture("fixtures/openai-chat/text-request.json");
        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        assertThat(normalized.getModel()).isEqualTo("gpt-4");
        assertThat(normalized.getTemperature()).isEqualTo(0.7);
        assertThat(normalized.getMaxOutputTokens()).isEqualTo(1024);
        assertThat(normalized.getStream()).isFalse();
        assertThat(normalized.getMessages()).hasSize(2);

        // System message
        CanonicalMessage sysMsg = normalized.getMessages().get(0);
        assertThat(sysMsg.getRole()).isEqualTo(CanonicalRole.SYSTEM);
        assertThat(extractText(sysMsg)).isEqualTo("You are a helpful assistant.");

        // User message
        CanonicalMessage userMsg = normalized.getMessages().get(1);
        assertThat(userMsg.getRole()).isEqualTo(CanonicalRole.USER);
        assertThat(extractText(userMsg)).isEqualTo("Hello, how are you?");
    }

    @Test
    void normalizeImageRequest() throws IOException {
        JsonNode request = loadFixture("fixtures/openai-chat/image-request.json");
        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        assertThat(normalized.getMessages()).hasSize(1);
        CanonicalMessage msg = normalized.getMessages().get(0);
        assertThat(msg.getRole()).isEqualTo(CanonicalRole.USER);
        assertThat(msg.getContent()).hasSize(2);
        assertThat(msg.getContent().get(0)).isInstanceOf(TextContentPart.class);
        assertThat(msg.getContent().get(1)).isInstanceOf(ImageContentPart.class);

        ImageContentPart img = (ImageContentPart) msg.getContent().get(1);
        assertThat(img.getUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(img.getDetail()).isEqualTo("high");
    }

    @Test
    void normalizeToolRequest() throws IOException {
        JsonNode request = loadFixture("fixtures/openai-chat/tool-request.json");
        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        assertThat(normalized.getTools()).hasSize(1);
        assertThat(normalized.getTools().get(0).getName()).isEqualTo("get_weather");
        assertThat(normalized.getTools().get(0).getDescription()).isNotEmpty();
        assertThat(normalized.getToolChoice()).isNotNull();
        assertThat(normalized.getToolChoice().asString()).isEqualTo("auto");
    }

    @Test
    void normalizeToolResponse() throws IOException {
        JsonNode response = loadFixture("fixtures/openai-chat/tool-response.json");
        CanonicalResponse normalized = codec.normalizeResponse(response, context);

        assertThat(normalized.getId()).isEqualTo("chatcmpl-123");
        assertThat(normalized.getModel()).isEqualTo("gpt-4");
        assertThat(normalized.getStopReason()).isEqualTo("tool_use");
        assertThat(normalized.getOutputMessages()).hasSize(1);

        CanonicalMessage msg = normalized.getOutputMessages().get(0);
        assertThat(msg.getRole()).isEqualTo(CanonicalRole.ASSISTANT);
        assertThat(msg.getToolCalls()).hasSize(1);
        assertThat(msg.getToolCalls().get(0).getId()).isEqualTo("call_abc123");
        assertThat(msg.getToolCalls().get(0).getName()).isEqualTo("get_weather");

        assertThat(normalized.getUsage()).isNotNull();
        assertThat(normalized.getUsage().getInputTokens()).isEqualTo(10);
        assertThat(normalized.getUsage().getOutputTokens()).isEqualTo(5);
        assertThat(normalized.getUsage().getTotalTokens()).isEqualTo(15);
    }

    @Test
    void normalizeTextResponse() throws IOException {
        JsonNode response = loadFixture("fixtures/openai-chat/response.json");
        CanonicalResponse normalized = codec.normalizeResponse(response, context);

        assertThat(normalized.getId()).isEqualTo("chatcmpl-123");
        assertThat(normalized.getStopReason()).isEqualTo("end_turn");
        assertThat(normalized.getOutputMessages()).hasSize(1);
        assertThat(extractText(normalized.getOutputMessages().get(0)))
                .isEqualTo("Hello! I'm doing well, thank you for asking. How can I help you today?");
    }

    @Test
    void denormalizeTextRequest() {
        CanonicalRequest request = new CanonicalRequest();
        request.setModel("gpt-4");
        request.setTemperature(0.7);
        request.setMaxOutputTokens(1024);
        request.setStream(false);
        request.setMessages(List.of(
                new CanonicalMessage(CanonicalRole.SYSTEM, List.of(new TextContentPart("You are helpful."))),
                new CanonicalMessage(CanonicalRole.USER, List.of(new TextContentPart("Hello")))
        ));

        ObjectNode denormalized = codec.denormalizeRequest(request, context);

        assertThat(denormalized.get("model").asText()).isEqualTo("gpt-4");
        assertThat(denormalized.get("temperature").asDouble()).isEqualTo(0.7);
        assertThat(denormalized.get("max_tokens").asInt()).isEqualTo(1024);
        assertThat(denormalized.get("stream").asBoolean()).isFalse();
        assertThat(denormalized.get("messages")).hasSize(2);
    }

    @Test
    void denormalizeTextResponse() {
        CanonicalResponse response = new CanonicalResponse();
        response.setId("chatcmpl-123");
        response.setModel("gpt-4");
        response.setCreated(1234567890L);
        response.setStopReason("end_turn");
        response.setOutputMessages(List.of(
                new CanonicalMessage(CanonicalRole.ASSISTANT, List.of(new TextContentPart("Hello!")))
        ));
        CanonicalUsage usage = new CanonicalUsage();
        usage.setInputTokens(10);
        usage.setOutputTokens(5);
        usage.setTotalTokens(15);
        response.setUsage(usage);

        ObjectNode denormalized = codec.denormalizeResponse(response, context);

        assertThat(denormalized.get("id").asText()).isEqualTo("chatcmpl-123");
        assertThat(denormalized.get("object").asText()).isEqualTo("chat.completion");
        assertThat(denormalized.get("model").asText()).isEqualTo("gpt-4");
        assertThat(denormalized.get("choices")).hasSize(1);
        assertThat(denormalized.get("choices").get(0).get("finish_reason").asText()).isEqualTo("stop");
        assertThat(denormalized.get("usage").get("prompt_tokens").asInt()).isEqualTo(10);
    }

    @Test
    void roundTripTextRequest() throws IOException {
        JsonNode original = loadFixture("fixtures/openai-chat/text-request.json");
        CanonicalRequest normalized = codec.normalizeRequest(original, context);
        ObjectNode denormalized = codec.denormalizeRequest(normalized, context);

        assertThat(denormalized.get("model").asText()).isEqualTo(original.get("model").asText());
        assertThat(denormalized.get("temperature").asDouble()).isEqualTo(original.get("temperature").asDouble());
        assertThat(denormalized.get("messages")).hasSize(original.get("messages").size());
    }

    @Test
    void roundTripTextResponse() throws IOException {
        JsonNode original = loadFixture("fixtures/openai-chat/response.json");
        CanonicalResponse normalized = codec.normalizeResponse(original, context);
        ObjectNode denormalized = codec.denormalizeResponse(normalized, context);

        assertThat(denormalized.get("model").asText()).isEqualTo(original.get("model").asText());
        assertThat(denormalized.get("choices")).hasSize(1);
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
