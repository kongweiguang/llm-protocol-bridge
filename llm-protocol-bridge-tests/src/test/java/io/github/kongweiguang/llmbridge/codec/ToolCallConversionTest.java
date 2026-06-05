package io.github.kongweiguang.llmbridge.codec;

import io.github.kongweiguang.llmbridge.core.codec.*;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.json.JacksonUtil;
import io.github.kongweiguang.llmbridge.core.canonical.*;
import io.github.kongweiguang.llmbridge.core.stream.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for tool call conversion across all three protocols.
 * Verifies that tool call IDs, names, arguments, and order are preserved.
 */
class ToolCallConversionTest {

    private ProtocolCodecRegistry registry;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = new ProtocolCodecRegistry();
        mapper = new ObjectMapper();
    }

    @Test
    void openAiChatToolCall_toAnthropic() {
        // Build OpenAI Chat request with tool call
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "gpt-4");

        var messages = request.putArray("messages");
        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "What's the weather in NYC?");

        var assistantMsg = messages.addObject();
        assistantMsg.put("role", "assistant");
        var toolCalls = assistantMsg.putArray("tool_calls");
        var tc = toolCalls.addObject();
        tc.put("id", "call_abc123");
        tc.put("type", "function");
        var func = tc.putObject("function");
        func.put("name", "get_weather");
        func.put("arguments", "{\"location\":\"NYC\"}");

        var toolResult = messages.addObject();
        toolResult.put("role", "tool");
        toolResult.put("tool_call_id", "call_abc123");
        toolResult.put("content", "Sunny, 72°F");

        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(request, context);

        // Verify normalized tool call
        assertThat(normalized.getMessages()).hasSize(3);
        CanonicalMessage assistant = normalized.getMessages().get(1);
        assertThat(assistant.getToolCalls()).hasSize(1);
        assertThat(assistant.getToolCalls().get(0).getId()).isEqualTo("call_abc123");
        assertThat(assistant.getToolCalls().get(0).getName()).isEqualTo("get_weather");
        assertThat(assistant.getToolCalls().get(0).getRawArguments()).isEqualTo("{\"location\":\"NYC\"}");

        CanonicalMessage toolResultMsg = normalized.getMessages().get(2);
        assertThat(toolResultMsg.getToolCallId()).isEqualTo("call_abc123");

        // Denormalize to Anthropic
        ObjectNode anthropicRequest = targetCodec.denormalizeRequest(normalized, context);

        // Verify Anthropic format: user + assistant(tool_use) + user(tool_result)
        assertThat(anthropicRequest.get("messages")).hasSize(3);

        // Assistant message (index 1) should have tool_use content block
        JsonNode assistantContent = anthropicRequest.get("messages").get(1).get("content");
        assertThat(assistantContent).hasSize(1);
        assertThat(assistantContent.get(0).get("type").asText()).isEqualTo("tool_use");
        assertThat(assistantContent.get(0).get("id").asText()).isEqualTo("call_abc123");
        assertThat(assistantContent.get(0).get("name").asText()).isEqualTo("get_weather");
        assertThat(assistantContent.get(0).get("input").get("location").asText()).isEqualTo("NYC");

        // Tool result should be in user message (index 2)
        JsonNode toolResultContent = anthropicRequest.get("messages").get(2).get("content");
        assertThat(toolResultContent).hasSize(1);
        assertThat(toolResultContent.get(0).get("type").asText()).isEqualTo("tool_result");
        assertThat(toolResultContent.get(0).get("tool_use_id").asText()).isEqualTo("call_abc123");
        assertThat(toolResultContent.get(0).get("content").asText()).isEqualTo("Sunny, 72°F");
    }

    @Test
    void anthropicToolUse_toOpenAiChat() {
        // Build Anthropic response with tool_use
        ObjectNode response = JacksonUtil.objectNode();
        response.put("id", "msg-123");
        response.put("type", "message");
        response.put("role", "assistant");
        response.put("model", "claude-sonnet-4-6");
        response.put("stop_reason", "tool_use");

        var content = response.putArray("content");
        var textBlock = content.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", "Let me check the weather.");

        var toolUse = content.addObject();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_abc123");
        toolUse.put("name", "get_weather");
        var input = toolUse.putObject("input");
        input.put("location", "NYC");

        var usage = response.putObject("usage");
        usage.put("input_tokens", 10);
        usage.put("output_tokens", 20);

        ProtocolCodec sourceCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "claude-sonnet-4-6");

        CanonicalResponse normalized = sourceCodec.normalizeResponse(response, context);

        // Verify normalized
        assertThat(normalized.getStopReason()).isEqualTo("tool_use");
        assertThat(normalized.getOutputMessages().get(0).getToolCalls()).hasSize(1);
        assertThat(normalized.getOutputMessages().get(0).getToolCalls().get(0).getId()).isEqualTo("toolu_abc123");
        assertThat(normalized.getOutputMessages().get(0).getToolCalls().get(0).getName()).isEqualTo("get_weather");

        // Denormalize to OpenAI Chat
        ObjectNode chatResponse = targetCodec.denormalizeResponse(normalized, context);

        // Verify OpenAI Chat format
        assertThat(chatResponse.get("object").asText()).isEqualTo("chat.completion");
        assertThat(chatResponse.get("choices").get(0).get("finish_reason").asText()).isEqualTo("tool_calls");

        JsonNode message = chatResponse.get("choices").get(0).get("message");
        assertThat(message.get("tool_calls")).hasSize(1);
        assertThat(message.get("tool_calls").get(0).get("id").asText()).isEqualTo("toolu_abc123");
        assertThat(message.get("tool_calls").get(0).get("type").asText()).isEqualTo("function");
        assertThat(message.get("tool_calls").get(0).get("function").get("name").asText()).isEqualTo("get_weather");
    }

    @Test
    void openAiChatToolCall_toResponses() {
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

        var toolResult = messages.addObject();
        toolResult.put("role", "tool");
        toolResult.put("tool_call_id", "call_123");
        toolResult.put("content", "Sunny");

        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_RESPONSES);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_RESPONSES, "gpt-4");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(request, context);
        ObjectNode responsesRequest = targetCodec.denormalizeRequest(normalized, context);

        // Verify Responses format has function_call items
        JsonNode input = responsesRequest.get("input");
        assertThat(input).hasSize(2); // function_call + function_call_output

        assertThat(input.get(0).get("type").asText()).isEqualTo("function_call");
        assertThat(input.get(0).get("call_id").asText()).isEqualTo("call_123");
        assertThat(input.get(0).get("name").asText()).isEqualTo("get_weather");

        assertThat(input.get(1).get("type").asText()).isEqualTo("function_call_output");
        assertThat(input.get(1).get("call_id").asText()).isEqualTo("call_123");
        assertThat(input.get(1).get("output").asText()).isEqualTo("Sunny");
    }

    @Test
    void streamToolCall_openAiChat_toAnthropic() {
        Flux<CanonicalStreamEvent> events = Flux.just(
                new CanonicalStreamEvent(CanonicalStreamEventType.TOOL_CALL_START) {{
                    setToolCallId("call_123");
                    setToolName("get_weather");
                    setToolIndex(0);
                }},
                new CanonicalStreamEvent(CanonicalStreamEventType.TOOL_ARGUMENTS_DELTA) {{
                    setToolArgumentsDelta("{\"location\":");
                    setToolIndex(0);
                }},
                new CanonicalStreamEvent(CanonicalStreamEventType.TOOL_ARGUMENTS_DELTA) {{
                    setToolArgumentsDelta("\"NYC\"}");
                    setToolIndex(0);
                }},
                new CanonicalStreamEvent(CanonicalStreamEventType.TOOL_CALL_DONE) {{
                    setToolIndex(0);
                }},
                new CanonicalStreamEvent(CanonicalStreamEventType.DONE) {{
                    setStopReason("tool_use");
                }}
        );

        ProtocolCodec codec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        Flux<SseFrame> sseEvents = codec.denormalizeStream(events, context);

        StepVerifier.create(sseEvents.collectList())
                .assertNext(eventList -> {
                    // Should have: content_block_start(tool_use), 2x content_block_delta(input_json),
                    // content_block_stop, message_delta(stop_reason), message_stop
                    assertThat(eventList).isNotEmpty();

                    // Verify tool_use content_block_start
                    SseFrame startEvent = eventList.stream()
                            .filter(e -> "content_block_start".equals(e.getEvent()))
                            .findFirst().orElse(null);
                    assertThat(startEvent).isNotNull();
                    JsonNode startData = JacksonUtil.tryParse(startEvent.getData());
                    assertThat(startData.get("content_block").get("type").asText()).isEqualTo("tool_use");
                    assertThat(startData.get("content_block").get("id").asText()).isEqualTo("call_123");
                    assertThat(startData.get("content_block").get("name").asText()).isEqualTo("get_weather");

                    // Verify input_json deltas
                    List<SseFrame> jsonDeltas = eventList.stream()
                            .filter(e -> "content_block_delta".equals(e.getEvent()))
                            .toList();
                    assertThat(jsonDeltas).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void multipleToolCalls_orderPreserved() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "gpt-4");

        var messages = request.putArray("messages");
        var assistantMsg = messages.addObject();
        assistantMsg.put("role", "assistant");
        var toolCalls = assistantMsg.putArray("tool_calls");

        // First tool call
        var tc1 = toolCalls.addObject();
        tc1.put("id", "call_1");
        tc1.put("index", 0);
        tc1.put("type", "function");
        var func1 = tc1.putObject("function");
        func1.put("name", "get_weather");
        func1.put("arguments", "{\"location\":\"NYC\"}");

        // Second tool call
        var tc2 = toolCalls.addObject();
        tc2.put("id", "call_2");
        tc2.put("index", 1);
        tc2.put("type", "function");
        var func2 = tc2.putObject("function");
        func2.put("name", "get_time");
        func2.put("arguments", "{\"timezone\":\"EST\"}");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalRequest normalized = codec.normalizeRequest(request, context);

        // Verify order preserved
        assertThat(normalized.getMessages().get(0).getToolCalls()).hasSize(2);
        assertThat(normalized.getMessages().get(0).getToolCalls().get(0).getName()).isEqualTo("get_weather");
        assertThat(normalized.getMessages().get(0).getToolCalls().get(0).getIndex()).isEqualTo(0);
        assertThat(normalized.getMessages().get(0).getToolCalls().get(1).getName()).isEqualTo("get_time");
        assertThat(normalized.getMessages().get(0).getToolCalls().get(1).getIndex()).isEqualTo(1);

        // Denormalize and verify order
        ObjectNode denormalized = codec.denormalizeRequest(normalized, context);
        JsonNode denormalizedTcs = denormalized.get("messages").get(0).get("tool_calls");
        assertThat(denormalizedTcs).hasSize(2);
        assertThat(denormalizedTcs.get(0).get("function").get("name").asText()).isEqualTo("get_weather");
        assertThat(denormalizedTcs.get(1).get("function").get("name").asText()).isEqualTo("get_time");
    }

    @Test
    void openAiChatParallelToolCalls_preservedWhenConvertingToResponses() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "gpt-4");
        request.put("parallel_tool_calls", false);
        request.putArray("messages").addObject()
                .put("role", "user")
                .put("content", "Use one tool at a time.");

        ProtocolCodec sourceCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_RESPONSES);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_RESPONSES, "gpt-4");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(request, context);
        ObjectNode responsesRequest = targetCodec.denormalizeRequest(normalized, context);

        assertThat(normalized.getParallelToolCalls()).isFalse();
        assertThat(responsesRequest.get("parallel_tool_calls").asBoolean()).isFalse();
    }

    @Test
    void anthropicRequestToolUseAndResult_toOpenAiChatToolCallsAndToolMessage() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "claude-sonnet-4-6");
        request.put("max_tokens", 100);

        var messages = request.putArray("messages");
        var assistantMsg = messages.addObject();
        assistantMsg.put("role", "assistant");
        var assistantContent = assistantMsg.putArray("content");
        assistantContent.addObject()
                .put("type", "text")
                .put("text", "I'll check.");
        var toolUse = assistantContent.addObject();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_123");
        toolUse.put("name", "get_weather");
        toolUse.putObject("input").put("location", "NYC");

        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        var userContent = userMsg.putArray("content");
        var toolResult = userContent.addObject();
        toolResult.put("type", "tool_result");
        toolResult.put("tool_use_id", "toolu_123");
        toolResult.put("content", "Sunny");
        toolResult.put("is_error", true);

        ProtocolCodec sourceCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "claude-sonnet-4-6");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(request, context);
        ObjectNode chatRequest = targetCodec.denormalizeRequest(normalized, context);

        JsonNode chatMessages = chatRequest.get("messages");
        assertThat(chatMessages).hasSize(2);

        JsonNode assistant = chatMessages.get(0);
        assertThat(assistant.get("role").asText()).isEqualTo("assistant");
        assertThat(assistant.get("content").asText()).isEqualTo("I'll check.");
        assertThat(assistant.get("tool_calls")).hasSize(1);
        assertThat(assistant.get("tool_calls").get(0).get("id").asText()).isEqualTo("toolu_123");
        assertThat(assistant.get("tool_calls").get(0).get("function").get("name").asText()).isEqualTo("get_weather");
        assertThat(assistant.get("tool_calls").get(0).get("function").get("arguments").asText())
                .isEqualTo("{\"location\":\"NYC\"}");

        JsonNode tool = chatMessages.get(1);
        assertThat(tool.get("role").asText()).isEqualTo("tool");
        assertThat(tool.get("tool_call_id").asText()).isEqualTo("toolu_123");
        assertThat(tool.get("content").asText()).isEqualTo("Sunny");
    }

    @Test
    void anthropicMultipleToolResults_toOpenAiChatSeparateToolMessages() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "claude-sonnet-4-6");
        request.put("max_tokens", 100);

        var messages = request.putArray("messages");
        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        var userContent = userMsg.putArray("content");
        var firstResult = userContent.addObject();
        firstResult.put("type", "tool_result");
        firstResult.put("tool_use_id", "toolu_weather");
        firstResult.put("content", "Sunny");
        var secondResult = userContent.addObject();
        secondResult.put("type", "tool_result");
        secondResult.put("tool_use_id", "toolu_time");
        secondResult.put("content", "10:30");

        ProtocolCodec sourceCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec targetCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "claude-sonnet-4-6");

        CanonicalRequest normalized = sourceCodec.normalizeRequest(request, context);
        ObjectNode chatRequest = targetCodec.denormalizeRequest(normalized, context);

        assertThat(normalized.getMessages()).hasSize(2);
        assertThat(normalized.getMessages().get(0).getRole()).isEqualTo(CanonicalRole.TOOL);
        assertThat(normalized.getMessages().get(0).getToolCallId()).isEqualTo("toolu_weather");
        assertThat(normalized.getMessages().get(1).getRole()).isEqualTo(CanonicalRole.TOOL);
        assertThat(normalized.getMessages().get(1).getToolCallId()).isEqualTo("toolu_time");

        JsonNode chatMessages = chatRequest.get("messages");
        assertThat(chatMessages).hasSize(2);
        assertThat(chatMessages.get(0).get("role").asText()).isEqualTo("tool");
        assertThat(chatMessages.get(0).get("tool_call_id").asText()).isEqualTo("toolu_weather");
        assertThat(chatMessages.get(0).get("content").asText()).isEqualTo("Sunny");
        assertThat(chatMessages.get(1).get("role").asText()).isEqualTo("tool");
        assertThat(chatMessages.get(1).get("tool_call_id").asText()).isEqualTo("toolu_time");
        assertThat(chatMessages.get(1).get("content").asText()).isEqualTo("10:30");
    }

    @Test
    void canonicalResponseMultipleOutputMessages_toAnthropicPreservesAllContentBlocks() {
        CanonicalResponse response = new CanonicalResponse();
        response.setId("resp_123");
        response.setModel("claude-sonnet-4-6");

        CanonicalMessage first = new CanonicalMessage();
        first.setRole(CanonicalRole.ASSISTANT);
        first.setContent(List.of(new TextContentPart("First part.")));

        CanonicalToolCall tc = new CanonicalToolCall();
        tc.setId("toolu_123");
        tc.setName("get_weather");
        tc.setType("function");
        ObjectNode args = JacksonUtil.objectNode();
        args.put("location", "NYC");
        tc.setArguments(args);

        CanonicalMessage second = new CanonicalMessage();
        second.setRole(CanonicalRole.ASSISTANT);
        second.setToolCalls(List.of(tc));
        second.setContent(List.of(new TextContentPart("Second part.")));

        response.setOutputMessages(List.of(first, second));

        ProtocolCodec codec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext context = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        ObjectNode anthropicResponse = codec.denormalizeResponse(response, context);
        JsonNode content = anthropicResponse.get("content");

        assertThat(content).hasSize(3);
        assertThat(content.get(0).get("type").asText()).isEqualTo("text");
        assertThat(content.get(0).get("text").asText()).isEqualTo("First part.");
        assertThat(content.get(1).get("type").asText()).isEqualTo("tool_use");
        assertThat(content.get(1).get("id").asText()).isEqualTo("toolu_123");
        assertThat(content.get(1).get("name").asText()).isEqualTo("get_weather");
        assertThat(content.get(2).get("type").asText()).isEqualTo("text");
        assertThat(content.get(2).get("text").asText()).isEqualTo("Second part.");
    }

    @Test
    void structuredToolChoice_mapsBetweenOpenAiAndAnthropicShapes() {
        ObjectNode request = JacksonUtil.objectNode();
        request.put("model", "gpt-4");
        request.putArray("messages").addObject()
                .put("role", "user")
                .put("content", "Call get_weather.");
        var toolChoice = request.putObject("tool_choice");
        toolChoice.put("type", "function");
        toolChoice.putObject("function").put("name", "get_weather");

        ProtocolCodec chatCodec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec anthropicCodec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext toAnthropic = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.ANTHROPIC_MESSAGES, "gpt-4");

        CanonicalRequest normalized = chatCodec.normalizeRequest(request, toAnthropic);
        ObjectNode anthropicRequest = anthropicCodec.denormalizeRequest(normalized, toAnthropic);

        assertThat(anthropicRequest.get("tool_choice").get("type").asText()).isEqualTo("tool");
        assertThat(anthropicRequest.get("tool_choice").get("name").asText()).isEqualTo("get_weather");

        ObjectNode anthropicSource = JacksonUtil.objectNode();
        anthropicSource.put("model", "claude-sonnet-4-6");
        anthropicSource.put("max_tokens", 100);
        anthropicSource.putArray("messages").addObject()
                .put("role", "user")
                .put("content", "Call get_weather.");
        var anthropicToolChoice = anthropicSource.putObject("tool_choice");
        anthropicToolChoice.put("type", "tool");
        anthropicToolChoice.put("name", "get_weather");

        ProtocolCodec.BridgeContext toChat = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "claude-sonnet-4-6");
        CanonicalRequest normalizedAnthropic = anthropicCodec.normalizeRequest(anthropicSource, toChat);
        ObjectNode chatRequest = chatCodec.denormalizeRequest(normalizedAnthropic, toChat);

        assertThat(chatRequest.get("tool_choice").get("type").asText()).isEqualTo("function");
        assertThat(chatRequest.get("tool_choice").get("function").get("name").asText()).isEqualTo("get_weather");
    }
}
