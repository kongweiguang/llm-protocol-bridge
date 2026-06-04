package io.github.kongweiguang.llmbridge.codec;

import io.github.kongweiguang.llmbridge.core.codec.ProtocolCodec;
import io.github.kongweiguang.llmbridge.core.codec.ProtocolCodecRegistry;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SDK compatibility tests verifying that common SDK request formats
 * are correctly parsed by the codecs.
 */
class SdkCompatibilityTest {

    private ProtocolCodecRegistry registry;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = new ProtocolCodecRegistry();
        mapper = new ObjectMapper();
    }

    // ===== OpenAI Chat SDK format =====

    @Test
    void openAiChatSdk_standardRequest() {
        // Simulates: openai.chat.completions.create(model="gpt-4", messages=[...])
        ObjectNode request = mapper.createObjectNode();
        request.put("model", "gpt-4");
        request.put("temperature", 0.7);
        request.put("stream", false);
        var messages = request.putArray("messages");
        var sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", "You are a helpful assistant.");
        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Hello!");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalRequest normalized = codec.normalizeRequest(request, ctx);

        assertThat(normalized.getModel()).isEqualTo("gpt-4");
        assertThat(normalized.getTemperature()).isEqualTo(0.7);
        assertThat(normalized.getMessages()).hasSize(2);
        assertThat(normalized.getMessages().get(0).getRole().name()).isEqualTo("SYSTEM");
        assertThat(normalized.getMessages().get(1).getRole().name()).isEqualTo("USER");
    }

    @Test
    void openAiChatSdk_withToolCalls() {
        ObjectNode request = mapper.createObjectNode();
        request.put("model", "gpt-4");
        var messages = request.putArray("messages");
        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "What's the weather?");

        var assistantMsg = messages.addObject();
        assistantMsg.put("role", "assistant");
        var toolCalls = assistantMsg.putArray("tool_calls");
        var tc = toolCalls.addObject();
        tc.put("id", "call_123");
        tc.put("type", "function");
        var func = tc.putObject("function");
        func.put("name", "get_weather");
        func.put("arguments", "{\"city\":\"Beijing\"}");

        var toolMsg = messages.addObject();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", "call_123");
        toolMsg.put("content", "Sunny, 25°C");

        var tools = request.putArray("tools");
        var tool = tools.addObject();
        tool.put("type", "function");
        var toolFunc = tool.putObject("function");
        toolFunc.put("name", "get_weather");
        toolFunc.put("description", "Get weather info");
        toolFunc.putObject("parameters").put("type", "object");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_CHAT_COMPLETIONS);
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4");

        CanonicalRequest normalized = codec.normalizeRequest(request, ctx);

        assertThat(normalized.getMessages()).hasSize(3);
        assertThat(normalized.getMessages().get(1).getToolCalls()).isNotNull();
        assertThat(normalized.getMessages().get(1).getToolCalls().get(0).getId()).isEqualTo("call_123");
        assertThat(normalized.getMessages().get(1).getToolCalls().get(0).getName()).isEqualTo("get_weather");
        assertThat(normalized.getMessages().get(2).getToolCallId()).isEqualTo("call_123");
        assertThat(normalized.getTools()).hasSize(1);
        assertThat(normalized.getTools().get(0).getName()).isEqualTo("get_weather");
    }

    // ===== OpenAI Responses SDK format =====

    @Test
    void openAiResponsesSdk_standardRequest() {
        // Simulates: openai.responses.create(model="gpt-4o", input="Hello")
        ObjectNode request = mapper.createObjectNode();
        request.put("model", "gpt-4o");
        request.put("input", "Hello");
        request.put("instructions", "You are helpful.");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_RESPONSES);
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.OPENAI_RESPONSES, "gpt-4o");

        CanonicalRequest normalized = codec.normalizeRequest(request, ctx);

        assertThat(normalized.getModel()).isEqualTo("gpt-4o");
        assertThat(normalized.getMessages()).hasSize(2); // instructions + input
        assertThat(normalized.getMessages().get(0).getRole().name()).isEqualTo("SYSTEM");
        assertThat(normalized.getMessages().get(1).getRole().name()).isEqualTo("USER");
    }

    @Test
    void openAiResponsesSdk_arrayInput() {
        ObjectNode request = mapper.createObjectNode();
        request.put("model", "gpt-4o");
        var input = request.putArray("input");
        var msg = input.addObject();
        msg.put("type", "message");
        msg.put("role", "user");
        var content = msg.putArray("content");
        var textPart = content.addObject();
        textPart.put("type", "input_text");
        textPart.put("text", "Hello");

        ProtocolCodec codec = registry.get(ApiProtocol.OPENAI_RESPONSES);
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.OPENAI_RESPONSES, "gpt-4o");

        CanonicalRequest normalized = codec.normalizeRequest(request, ctx);

        assertThat(normalized.getMessages()).hasSize(1);
        assertThat(normalized.getMessages().get(0).getRole().name()).isEqualTo("USER");
    }

    // ===== Anthropic SDK format =====

    @Test
    void anthropicSdk_standardRequest() {
        // Simulates: anthropic.messages.create(model="claude-sonnet-4-6", max_tokens=1024, messages=[...])
        ObjectNode request = mapper.createObjectNode();
        request.put("model", "claude-sonnet-4-6");
        request.put("max_tokens", 1024);
        request.put("system", "You are Claude.");
        var messages = request.putArray("messages");
        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Hello!");

        ProtocolCodec codec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        CanonicalRequest normalized = codec.normalizeRequest(request, ctx);

        assertThat(normalized.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(normalized.getMaxOutputTokens()).isEqualTo(1024);
        assertThat(normalized.getMessages()).hasSize(2); // system + user
        assertThat(normalized.getMessages().get(0).getRole().name()).isEqualTo("SYSTEM");
        assertThat(normalized.getMessages().get(1).getRole().name()).isEqualTo("USER");
    }

    @Test
    void anthropicSdk_withToolUse() {
        ObjectNode request = mapper.createObjectNode();
        request.put("model", "claude-sonnet-4-6");
        request.put("max_tokens", 1024);
        var messages = request.putArray("messages");

        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "What's the weather?");

        var assistantMsg = messages.addObject();
        assistantMsg.put("role", "assistant");
        var assistantContent = assistantMsg.putArray("content");
        var toolUse = assistantContent.addObject();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_123");
        toolUse.put("name", "get_weather");
        toolUse.putObject("input").put("city", "Beijing");

        var toolResultMsg = messages.addObject();
        toolResultMsg.put("role", "user");
        var trContent = toolResultMsg.putArray("content");
        var tr = trContent.addObject();
        tr.put("type", "tool_result");
        tr.put("tool_use_id", "toolu_123");
        tr.put("content", "Sunny, 25°C");

        var tools = request.putArray("tools");
        var tool = tools.addObject();
        tool.put("name", "get_weather");
        tool.put("description", "Get weather");
        tool.putObject("input_schema").put("type", "object");

        ProtocolCodec codec = registry.get(ApiProtocol.ANTHROPIC_MESSAGES);
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        CanonicalRequest normalized = codec.normalizeRequest(request, ctx);

        assertThat(normalized.getMessages()).hasSize(3);
        // Anthropic codec puts tool_use in content as ToolCallContentPart
        assertThat(normalized.getMessages().get(1).getContent()).isNotNull();
        boolean hasToolCall = normalized.getMessages().get(1).getContent().stream()
                .anyMatch(p -> p instanceof io.github.kongweiguang.llmbridge.core.canonical.ToolCallContentPart tcp
                        && "toolu_123".equals(tcp.getId()));
        assertThat(hasToolCall).isTrue();
        assertThat(normalized.getTools()).hasSize(1);
        assertThat(normalized.getTools().get(0).getName()).isEqualTo("get_weather");
    }
}
