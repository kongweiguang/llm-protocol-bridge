package io.github.kongweiguang.llmbridge.codec;

import io.github.kongweiguang.llmbridge.core.codec.*;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.json.JacksonUtil;
import io.github.kongweiguang.llmbridge.core.canonical.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying that unknown fields are preserved in rawExtra during protocol conversion.
 * This ensures no silent field dropping occurs.
 */
class RawExtraPreservationTest {

    private ProtocolCodecRegistry registry;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = new ProtocolCodecRegistry();
        mapper = new ObjectMapper();
    }

    @Test
    void openAiChatRequest_unknownFieldsPreserved() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "gpt-4");
        request.put("temperature", 0.7);
        request.put("custom_field", "custom_value");
        request.put("another_unknown", 42);
        ObjectNode nested = request.putObject("nested_unknown");
        nested.put("key", "value");

        var messages = request.putArray("messages");
        var msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", "Hello");
        msg.put("custom_message_field", "preserved");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        // Verify unknown fields are in rawExtra
        assertThat(normalized.getRawExtra()).isNotNull();
        assertThat(normalized.getRawExtra().get("custom_field").asText()).isEqualTo("custom_value");
        assertThat(normalized.getRawExtra().get("another_unknown").asInt()).isEqualTo(42);
        assertThat(normalized.getRawExtra().get("nested_unknown")).isNotNull();

        // Verify message unknown fields are preserved
        CanonicalMessage userMsg = normalized.getMessages().get(0);
        assertThat(userMsg.getRawExtra()).isNotNull();
        assertThat(userMsg.getRawExtra().get("custom_message_field").asText()).isEqualTo("preserved");
    }

    @Test
    void openAiChatRequest_unknownFieldsRoundTrip() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "gpt-4");
        request.put("temperature", 0.7);
        request.put("custom_field", "should_survive");

        var messages = request.putArray("messages");
        var msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", "Hello");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalRequest normalized = codec.normalizeRequest(request, context);
        ObjectNode denormalized = codec.denormalizeRequest(normalized, context);

        // Verify unknown field survives round-trip
        assertThat(denormalized.get("custom_field").asText()).isEqualTo("should_survive");
    }

    @Test
    void openAiChatToolCall_rawArgumentsPreserved() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "gpt-4");

        var messages = request.putArray("messages");
        var assistantMsg = messages.addObject();
        assistantMsg.put("role", "assistant");

        var toolCalls = assistantMsg.putArray("tool_calls");
        var tc = toolCalls.addObject();
        tc.put("id", "call_123");
        tc.put("type", "function");
        var func = tc.putObject("function");
        func.put("name", "get_weather");
        func.put("arguments", "{\"location\":\"NYC\"}");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        // Verify rawArguments is preserved
        CanonicalToolCall toolCall = normalized.getMessages().get(0).getToolCalls().get(0);
        assertThat(toolCall.getRawArguments()).isEqualTo("{\"location\":\"NYC\"}");
        assertThat(toolCall.getArguments()).isNotNull();
        assertThat(toolCall.getArguments().get("location").asText()).isEqualTo("NYC");
        assertThat(toolCall.getType()).isEqualTo("function");

        // Denormalize and verify rawArguments is used
        ObjectNode denormalized = codec.denormalizeRequest(normalized, context);
        JsonNode denormalizedTc = denormalized.get("messages").get(0).get("tool_calls").get(0);
        assertThat(denormalizedTc.get("function").get("arguments").asText()).isEqualTo("{\"location\":\"NYC\"}");
    }

    @Test
    void openAiChatResponse_unknownFieldsPreserved() {
        ObjectNode response = JacksonUtil.objectNode();
        response.put("id", "chatcmpl-123");
        response.put("object", "chat.completion");
        response.put("created", 1234567890);
        response.put("model", "gpt-4");
        response.put("system_fingerprint", "fp_123");
        response.put("custom_response_field", "preserved");

        var choices = response.putArray("choices");
        var choice = choices.addObject();
        choice.put("index", 0);
        var message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", "Hello");
        message.put("custom_msg_field", "also_preserved");
        choice.put("finish_reason", "stop");

        var usage = response.putObject("usage");
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 5);
        usage.put("total_tokens", 15);
        var promptDetails = usage.putObject("prompt_tokens_details");
        promptDetails.put("cached_tokens", 3);
        var completionDetails = usage.putObject("completion_tokens_details");
        completionDetails.put("reasoning_tokens", 2);

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalResponse normalized = codec.normalizeResponse(response, context);

        // Verify unknown response fields preserved
        assertThat(normalized.getRawExtra()).isNotNull();
        assertThat(normalized.getRawExtra().get("system_fingerprint").asText()).isEqualTo("fp_123");
        assertThat(normalized.getRawExtra().get("custom_response_field").asText()).isEqualTo("preserved");

        // Verify usage details
        assertThat(normalized.getUsage().getCachedInputTokens()).isEqualTo(3);
        assertThat(normalized.getUsage().getReasoningTokens()).isEqualTo(2);

        // Verify message unknown fields preserved
        CanonicalMessage msg = normalized.getOutputMessages().get(0);
        assertThat(msg.getRawExtra()).isNotNull();
        assertThat(msg.getRawExtra().get("custom_msg_field").asText()).isEqualTo("also_preserved");
    }

    @Test
    void anthropicRequest_unknownFieldsPreserved() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "claude-sonnet-4-6");
        request.put("max_tokens", 1024);
        request.put("custom_field", "custom_value");

        var messages = request.putArray("messages");
        var msg = messages.addObject();
        msg.put("role", "user");
        var content = msg.putArray("content");
        var textBlock = content.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", "Hello");
        textBlock.put("custom_block_field", "preserved");

        ProtocolCodec codec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        // Verify unknown fields preserved
        assertThat(normalized.getRawExtra()).isNotNull();
        assertThat(normalized.getRawExtra().get("custom_field").asText()).isEqualTo("custom_value");
    }

    @Test
    void anthropicToolRequest_inputSchemaPreserved() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "claude-sonnet-4-6");
        request.put("max_tokens", 1024);

        var messages = request.putArray("messages");
        var msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", "Hello");

        var tools = request.putArray("tools");
        var tool = tools.addObject();
        tool.put("name", "get_weather");
        tool.put("description", "Get weather info");
        var schema = tool.putObject("input_schema");
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var loc = props.putObject("location");
        loc.put("type", "string");
        schema.put("required", mapper.createArrayNode().add("location"));

        ProtocolCodec codec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        // Verify inputSchema preserved
        assertThat(normalized.getTools()).hasSize(1);
        assertThat(normalized.getTools().get(0).getInputSchema()).isNotNull();
        assertThat(normalized.getTools().get(0).getInputSchema().get("type").asText()).isEqualTo("object");

        // Convert to OpenAI Chat and verify schema survives
        ProtocolCodec chatCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ObjectNode chatRequest = chatCodec.denormalizeRequest(normalized, context);

        assertThat(chatRequest.get("tools")).hasSize(1);
        assertThat(chatRequest.get("tools").get(0).get("function").get("parameters")).isNotNull();
        assertThat(chatRequest.get("tools").get(0).get("function").get("parameters").get("type").asText())
                .isEqualTo("object");
    }

    @Test
    void unknownContentPart_preservedAsUnknownPart() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "gpt-4");

        var messages = request.putArray("messages");
        var msg = messages.addObject();
        msg.put("role", "user");
        var content = msg.putArray("content");
        var unknownBlock = content.addObject();
        unknownBlock.put("type", "future_type");
        unknownBlock.put("data", "some_value");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        // Verify unknown content part is preserved as UnknownContentPart
        assertThat(normalized.getMessages().get(0).getContent()).hasSize(1);
        assertThat(normalized.getMessages().get(0).getContent().get(0)).isInstanceOf(UnknownContentPart.class);
        UnknownContentPart unknown = (UnknownContentPart) normalized.getMessages().get(0).getContent().get(0);
        assertThat(unknown.getOriginalType()).isEqualTo("future_type");
        assertThat(unknown.getRaw().get("data").asText()).isEqualTo("some_value");
    }

    @Test
    void refusalPart_preservedInResponse() {
        ObjectNode response = JacksonUtil.objectNode();
        response.put("id", "chatcmpl-123");
        response.put("object", "chat.completion");
        response.put("created", 1234567890);
        response.put("model", "gpt-4");

        var choices = response.putArray("choices");
        var choice = choices.addObject();
        choice.put("index", 0);
        var message = choice.putObject("message");
        message.put("role", "assistant");
        message.putNull("content");
        message.put("refusal", "I cannot help with that request.");
        choice.put("finish_reason", "stop");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalResponse normalized = codec.normalizeResponse(response, context);

        // Verify refusal is preserved as RefusalContentPart
        assertThat(normalized.getOutputMessages()).hasSize(1);
        CanonicalMessage msg = normalized.getOutputMessages().get(0);
        boolean hasRefusal = msg.getContent().stream()
                .anyMatch(p -> p instanceof RefusalContentPart);
        assertThat(hasRefusal).isTrue();

        RefusalContentPart refusal = msg.getContent().stream()
                .filter(p -> p instanceof RefusalContentPart)
                .map(p -> (RefusalContentPart) p)
                .findFirst().orElse(null);
        assertThat(refusal).isNotNull();
        assertThat(refusal.getRefusal()).isEqualTo("I cannot help with that request.");

        // Denormalize and verify refusal survives
        ObjectNode denormalized = codec.denormalizeResponse(normalized, context);
        assertThat(denormalized.get("choices").get(0).get("message").get("refusal").asText())
                .isEqualTo("I cannot help with that request.");
    }
}
