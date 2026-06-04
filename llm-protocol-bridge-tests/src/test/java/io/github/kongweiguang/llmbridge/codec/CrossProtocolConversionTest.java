package io.github.kongweiguang.llmbridge.codec;

import io.github.kongweiguang.llmbridge.core.codec.*;
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
 * Tests for cross-protocol conversion (e.g., OpenAI Chat -> Anthropic, Anthropic -> OpenAI Chat, etc.).
 * Verifies the full normalize -> denormalize pipeline across different protocol codecs.
 */
class CrossProtocolConversionTest {

    private ProtocolCodecRegistry registry;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = new ProtocolCodecRegistry();
        mapper = new ObjectMapper();
    }

    // ===== OpenAI Chat -> Normalized -> Anthropic =====

    @Test
    void openAiChatToAnthropic_textRequest() throws IOException {
        JsonNode openAiRequest = loadFixture("fixtures/openai-chat/text-request.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(openAiRequest, context);
        ObjectNode anthropicRequest = targetCodec.denormalizeRequest(normalized, context);

        assertThat(anthropicRequest.get("model").asText()).isEqualTo("gpt-4");
        assertThat(anthropicRequest.get("system").asText()).isEqualTo("You are a helpful assistant.");
        assertThat(anthropicRequest.get("messages")).hasSize(1);
        assertThat(anthropicRequest.get("messages").get(0).get("role").asText()).isEqualTo("user");
        assertThat(anthropicRequest.get("max_tokens").asInt()).isEqualTo(1024);
        assertThat(anthropicRequest.get("temperature").asDouble()).isEqualTo(0.7);
    }

    @Test
    void openAiChatToAnthropic_toolRequest() throws IOException {
        JsonNode openAiRequest = loadFixture("fixtures/openai-chat/tool-request.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(openAiRequest, context);
        ObjectNode anthropicRequest = targetCodec.denormalizeRequest(normalized, context);

        assertThat(anthropicRequest.get("tools")).hasSize(1);
        assertThat(anthropicRequest.get("tools").get(0).get("name").asText()).isEqualTo("get_weather");
        assertThat(anthropicRequest.get("tools").get(0).has("input_schema")).isTrue();
    }

    @Test
    void openAiChatToAnthropic_response() throws IOException {
        JsonNode openAiResponse = loadFixture("fixtures/openai-chat/response.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalResponse normalized = sourceCodec.normalizeResponse(openAiResponse, context);
        ObjectNode anthropicResponse = targetCodec.denormalizeResponse(normalized, context);

        assertThat(anthropicResponse.get("type").asText()).isEqualTo("message");
        assertThat(anthropicResponse.get("role").asText()).isEqualTo("assistant");
        assertThat(anthropicResponse.get("stop_reason").asText()).isEqualTo("end_turn");
        assertThat(anthropicResponse.get("content")).hasSize(1);
        assertThat(anthropicResponse.get("content").get(0).get("type").asText()).isEqualTo("text");
    }

    // ===== Anthropic -> Normalized -> OpenAI Chat =====

    @Test
    void anthropicToOpenAiChat_textRequest() throws IOException {
        JsonNode anthropicRequest = loadFixture("fixtures/anthropic/text-request.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "claude-sonnet-4-6");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(anthropicRequest, context);
        ObjectNode openAiRequest = targetCodec.denormalizeRequest(normalized, context);

        assertThat(openAiRequest.get("model").asText()).isEqualTo("claude-sonnet-4-6");
        assertThat(openAiRequest.get("messages")).hasSize(2);
        assertThat(openAiRequest.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(openAiRequest.get("messages").get(1).get("role").asText()).isEqualTo("user");
        assertThat(openAiRequest.get("max_tokens").asInt()).isEqualTo(1024);
    }

    @Test
    void anthropicToOpenAiChat_response() throws IOException {
        JsonNode anthropicResponse = loadFixture("fixtures/anthropic/response.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "claude-sonnet-4-6");

        CanonicalResponse normalized = sourceCodec.normalizeResponse(anthropicResponse, context);
        ObjectNode openAiResponse = targetCodec.denormalizeResponse(normalized, context);

        assertThat(openAiResponse.get("object").asText()).isEqualTo("chat.completion");
        assertThat(openAiResponse.get("choices")).hasSize(1);
        assertThat(openAiResponse.get("choices").get(0).get("finish_reason").asText()).isEqualTo("stop");
    }

    // ===== OpenAI Responses -> Normalized -> Anthropic =====

    @Test
    void openAiResponsesToAnthropic_textRequest() throws IOException {
        JsonNode responsesRequest = loadFixture("fixtures/openai-responses/text-request.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_RESPONSES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(responsesRequest, context);
        ObjectNode anthropicRequest = targetCodec.denormalizeRequest(normalized, context);

        assertThat(anthropicRequest.get("model").asText()).isEqualTo("gpt-4");
        assertThat(anthropicRequest.get("system").asText()).isEqualTo("You are a helpful assistant.");
        assertThat(anthropicRequest.get("messages")).hasSize(1);
    }

    // ===== Anthropic -> Normalized -> OpenAI Responses =====

    @Test
    void anthropicToOpenAiResponses_textRequest() throws IOException {
        JsonNode anthropicRequest = loadFixture("fixtures/anthropic/text-request.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_RESPONSES);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.OPENAI_RESPONSES, "claude-sonnet-4-6");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(anthropicRequest, context);
        ObjectNode responsesRequest = targetCodec.denormalizeRequest(normalized, context);

        assertThat(responsesRequest.get("model").asText()).isEqualTo("claude-sonnet-4-6");
        assertThat(responsesRequest.get("instructions").asText()).isEqualTo("You are a helpful assistant.");
        assertThat(responsesRequest.get("input")).hasSize(1);
    }

    // ===== OpenAI Chat -> Normalized -> OpenAI Responses =====

    @Test
    void openAiChatToOpenAiResponses_textRequest() throws IOException {
        JsonNode chatRequest = loadFixture("fixtures/openai-chat/text-request.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_RESPONSES);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_RESPONSES, "gpt-4");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(chatRequest, context);
        ObjectNode responsesRequest = targetCodec.denormalizeRequest(normalized, context);

        assertThat(responsesRequest.get("model").asText()).isEqualTo("gpt-4");
        assertThat(responsesRequest.get("instructions").asText()).isEqualTo("You are a helpful assistant.");
        assertThat(responsesRequest.get("input")).hasSize(1);
    }

    // ===== OpenAI Responses -> Normalized -> OpenAI Chat =====

    @Test
    void openAiResponsesToOpenAiChat_textRequest() throws IOException {
        JsonNode responsesRequest = loadFixture("fixtures/openai-responses/text-request.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_RESPONSES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(responsesRequest, context);
        ObjectNode chatRequest = targetCodec.denormalizeRequest(normalized, context);

        assertThat(chatRequest.get("model").asText()).isEqualTo("gpt-4");
        assertThat(chatRequest.get("messages")).hasSize(2);
        assertThat(chatRequest.get("messages").get(0).get("role").asText()).isEqualTo("system");
    }

    // ===== Usage mapping tests =====

    @Test
    void usageMapping_openAiToAnthropic() throws IOException {
        JsonNode openAiResponse = loadFixture("fixtures/openai-chat/response.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalResponse normalized = sourceCodec.normalizeResponse(openAiResponse, context);
        ObjectNode anthropicResponse = targetCodec.denormalizeResponse(normalized, context);

        assertThat(anthropicResponse.get("usage").get("input_tokens").asInt()).isEqualTo(10);
        assertThat(anthropicResponse.get("usage").get("output_tokens").asInt()).isEqualTo(20);
    }

    @Test
    void usageMapping_openAiChatAlternateNamesToAnthropic() {
        ObjectNode openAiResponse = mapper.createObjectNode();
        openAiResponse.put("id", "chatcmpl-alt-usage");
        openAiResponse.put("object", "chat.completion");
        openAiResponse.put("created", 123);
        openAiResponse.put("model", "gpt-4");
        openAiResponse.putArray("choices");
        ObjectNode usage = openAiResponse.putObject("usage");
        usage.put("input_tokens", 11);
        usage.put("output_tokens", 7);
        usage.put("total_tokens", 18);

        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalResponse normalized = sourceCodec.normalizeResponse(openAiResponse, context);
        ObjectNode anthropicResponse = targetCodec.denormalizeResponse(normalized, context);

        assertThat(anthropicResponse.get("usage").get("input_tokens").asInt()).isEqualTo(11);
        assertThat(anthropicResponse.get("usage").get("output_tokens").asInt()).isEqualTo(7);
    }

    @Test
    void usageMapping_openAiResponsesAlternateNamesToAnthropic() {
        ObjectNode responsesResponse = mapper.createObjectNode();
        responsesResponse.put("id", "resp-alt-usage");
        responsesResponse.put("object", "response");
        responsesResponse.put("created_at", 123);
        responsesResponse.put("model", "gpt-4");
        responsesResponse.put("status", "completed");
        responsesResponse.putArray("output");
        ObjectNode usage = responsesResponse.putObject("usage");
        usage.put("prompt_tokens", 13);
        usage.put("completion_tokens", 5);

        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_RESPONSES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalResponse normalized = sourceCodec.normalizeResponse(responsesResponse, context);
        ObjectNode anthropicResponse = targetCodec.denormalizeResponse(normalized, context);

        assertThat(anthropicResponse.get("usage").get("input_tokens").asInt()).isEqualTo(13);
        assertThat(anthropicResponse.get("usage").get("output_tokens").asInt()).isEqualTo(5);
    }

    @Test
    void usageMapping_anthropicToOpenAi() throws IOException {
        JsonNode anthropicResponse = loadFixture("fixtures/anthropic/response.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "claude-sonnet-4-6");

        CanonicalResponse normalized = sourceCodec.normalizeResponse(anthropicResponse, context);
        ObjectNode openAiResponse = targetCodec.denormalizeResponse(normalized, context);

        assertThat(openAiResponse.get("usage").get("prompt_tokens").asInt()).isEqualTo(10);
        assertThat(openAiResponse.get("usage").get("completion_tokens").asInt()).isEqualTo(20);
        assertThat(openAiResponse.get("usage").get("total_tokens").asInt()).isEqualTo(30);
    }

    // ===== Stop reason mapping tests =====

    @Test
    void stopReasonMapping_toolUse() throws IOException {
        JsonNode toolResponse = loadFixture("fixtures/openai-chat/tool-response.json");
        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);

        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalResponse normalized = sourceCodec.normalizeResponse(toolResponse, context);
        assertThat(normalized.getStopReason()).isEqualTo("tool_use");

        ObjectNode anthropicResponse = targetCodec.denormalizeResponse(normalized, context);
        assertThat(anthropicResponse.get("stop_reason").asText()).isEqualTo("tool_use");
    }

    private JsonNode loadFixture(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).isNotNull();
            return mapper.readTree(is);
        }
    }
}
