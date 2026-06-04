package io.github.kongweiguang.llmbridge.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalRequest;
import io.github.kongweiguang.llmbridge.core.canonical.CanonicalResponse;
import io.github.kongweiguang.llmbridge.core.codec.AnthropicMessagesCodec;
import io.github.kongweiguang.llmbridge.core.codec.OpenAiChatCompletionsCodec;
import io.github.kongweiguang.llmbridge.core.codec.OpenAiResponsesCodec;
import io.github.kongweiguang.llmbridge.core.codec.ProtocolCodec;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for newly added field support across the three codecs.
 * Covers the gap between OpenAI / Responses / Anthropic official spec
 * and the codec implementations.
 */
class ExtendedFieldsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ============== OpenAI Chat Completions ==============

    @Test
    void chat_normalizeLogitBias() {
        OpenAiChatCompletionsCodec codec = new OpenAiChatCompletionsCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4o");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.putObject("logit_bias").put("50256", -100);
        body.put("logprobs", true);
        body.put("top_logprobs", 5);
        body.put("reasoning_effort", "high");
        body.putObject("web_search_options").put("search_context_size", "high");

        CanonicalRequest req = codec.normalizeRequest(body, ctx);

        assertThat(req.getLogitBias()).isNotNull();
        assertThat(req.getLogitBias().get("50256").asInt()).isEqualTo(-100);
        assertThat(req.getLogprobs()).isTrue();
        assertThat(req.getTopLogprobs()).isEqualTo(5);
        assertThat(req.getReasoningEffort()).isEqualTo("high");
        assertThat(req.getWebSearchOptions()).isNotNull();
    }

    @Test
    void chat_maxCompletionTokensPreferred() {
        OpenAiChatCompletionsCodec codec = new OpenAiChatCompletionsCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4o");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.put("max_tokens", 1000);
        body.put("max_completion_tokens", 2000);

        CanonicalRequest req = codec.normalizeRequest(body, ctx);
        // max_completion_tokens takes precedence
        assertThat(req.getMaxOutputTokens()).isEqualTo(2000);
    }

    @Test
    void chat_modalitiesAndAudio() {
        OpenAiChatCompletionsCodec codec = new OpenAiChatCompletionsCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4o");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.put("modalities", mapper.createArrayNode().add("text").add("audio"));
        body.putObject("audio").put("voice", "alloy").put("format", "wav");

        CanonicalRequest req = codec.normalizeRequest(body, ctx);
        assertThat(req.getModalities()).containsExactly("text", "audio");
        assertThat(req.getAudio()).isNotNull();
        assertThat(req.getAudio().get("voice").asText()).isEqualTo("alloy");
    }

    @Test
    void chat_promptCacheKeyAndStore() {
        OpenAiChatCompletionsCodec codec = new OpenAiChatCompletionsCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4o");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.put("prompt_cache_key", "user-123-session");
        body.put("store", true);

        CanonicalRequest req = codec.normalizeRequest(body, ctx);
        assertThat(req.getPromptCacheKey()).isEqualTo("user-123-session");
        assertThat(req.getStore()).isTrue();
    }

    @Test
    void chat_denormalizeLogitBias() {
        OpenAiChatCompletionsCodec codec = new OpenAiChatCompletionsCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4o");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.putObject("logit_bias").put("50256", -100);
        body.put("logprobs", true);
        body.put("top_logprobs", 5);

        CanonicalRequest req = codec.normalizeRequest(body, ctx);
        ObjectNode out = codec.denormalizeRequest(req, ctx);

        assertThat(out.get("logit_bias")).isNotNull();
        assertThat(out.get("logit_bias").get("50256").asInt()).isEqualTo(-100);
        assertThat(out.get("logprobs").asBoolean()).isTrue();
        assertThat(out.get("top_logprobs").asInt()).isEqualTo(5);
    }

    @Test
    void chat_normalizeResponseReasoningContent() {
        OpenAiChatCompletionsCodec codec = new OpenAiChatCompletionsCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4o");

        ObjectNode resp = mapper.createObjectNode();
        resp.put("id", "chatcmpl-1");
        resp.put("model", "gpt-4o");
        ObjectNode choice = mapper.createObjectNode();
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "assistant");
        msg.put("content", "Hello");
        msg.put("reasoning_content", "The user said hello, I should respond.");
        choice.set("message", msg);
        choice.put("finish_reason", "stop");
        resp.putArray("choices").add(choice);

        CanonicalResponse cr = codec.normalizeResponse(resp, ctx);
        assertThat(cr.getOutputMessages()).hasSize(1);
        assertThat(cr.getOutputMessages().get(0).getReasoningContent())
                .isEqualTo("The user said hello, I should respond.");

        ObjectNode out = codec.denormalizeResponse(cr, ctx);
        JsonNode firstMsg = out.get("choices").get(0).get("message");
        assertThat(firstMsg.get("reasoning_content").asText())
                .isEqualTo("The user said hello, I should respond.");
    }

    @Test
    void chat_normalizeResponseUsageDetails() {
        OpenAiChatCompletionsCodec codec = new OpenAiChatCompletionsCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-4o");

        ObjectNode resp = mapper.createObjectNode();
        resp.put("id", "chatcmpl-1");
        resp.put("model", "gpt-4o");
        resp.putArray("choices"); // empty
        ObjectNode usage = mapper.createObjectNode();
        usage.put("prompt_tokens", 100);
        usage.put("completion_tokens", 50);
        usage.put("total_tokens", 150);
        ObjectNode promptDetails = mapper.createObjectNode();
        promptDetails.put("cached_tokens", 80);
        promptDetails.put("audio_tokens", 0);
        usage.set("prompt_tokens_details", promptDetails);
        ObjectNode completionDetails = mapper.createObjectNode();
        completionDetails.put("reasoning_tokens", 20);
        usage.set("completion_tokens_details", completionDetails);
        resp.set("usage", usage);

        CanonicalResponse cr = codec.normalizeResponse(resp, ctx);
        assertThat(cr.getUsage().getCachedInputTokens()).isEqualTo(80);
        assertThat(cr.getUsage().getReasoningTokens()).isEqualTo(20);
        assertThat(cr.getUsage().getInputTokens()).isEqualTo(100);
    }

    // ============== OpenAI Responses ==============

    @Test
    void responses_normalizeUserAndCacheKey() {
        OpenAiResponsesCodec codec = new OpenAiResponsesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.OPENAI_RESPONSES, "gpt-4o");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.put("input", "Hi");
        body.put("user", "user-123");
        body.put("prompt_cache_key", "cache-key-1");
        body.put("safety_identifier", "safety-1");
        body.put("background", true);
        body.put("service_tier", "auto");

        CanonicalRequest req = codec.normalizeRequest(body, ctx);
        assertThat(req.getUser()).isEqualTo("user-123");
        assertThat(req.getPromptCacheKey()).isEqualTo("cache-key-1");
        assertThat(req.getSafetyIdentifier()).isEqualTo("safety-1");
        assertThat(req.getBackground()).isTrue();
        assertThat(req.getServiceTier()).isEqualTo("auto");
    }

    @Test
    void responses_normalizeConversation() {
        OpenAiResponsesCodec codec = new OpenAiResponsesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.OPENAI_RESPONSES, "gpt-4o");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.put("input", "Hi");
        body.putObject("conversation").put("id", "conv-1");
        body.putObject("prompt").put("id", "tmpl-1");

        CanonicalRequest req = codec.normalizeRequest(body, ctx);
        assertThat(req.getConversation()).isNotNull();
        assertThat(req.getConversation().get("id").asText()).isEqualTo("conv-1");
        assertThat(req.getPrompt()).isNotNull();
    }

    @Test
    void responses_normalizeStatusMapped() {
        OpenAiResponsesCodec codec = new OpenAiResponsesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.OPENAI_RESPONSES, "gpt-4o");

        ObjectNode resp = mapper.createObjectNode();
        resp.put("id", "resp-1");
        resp.put("model", "gpt-4o");
        resp.put("status", "in_progress");
        resp.putArray("output");

        CanonicalResponse cr = codec.normalizeResponse(resp, ctx);
        assertThat(cr.getStopReason()).isEqualTo("in_progress");

        resp.put("status", "cancelled");
        cr = codec.normalizeResponse(resp, ctx);
        assertThat(cr.getStopReason()).isEqualTo("cancelled");
    }

    @Test
    void responses_normalizeUsageWithDetails() {
        OpenAiResponsesCodec codec = new OpenAiResponsesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.OPENAI_RESPONSES, "gpt-4o");

        ObjectNode resp = mapper.createObjectNode();
        resp.put("id", "resp-1");
        resp.put("model", "gpt-4o");
        resp.putArray("output");
        ObjectNode usage = mapper.createObjectNode();
        usage.put("input_tokens", 100);
        usage.put("output_tokens", 50);
        usage.put("total_tokens", 150);
        ObjectNode inputDetails = mapper.createObjectNode();
        inputDetails.put("cached_tokens", 80);
        usage.set("input_tokens_details", inputDetails);
        resp.set("usage", usage);

        CanonicalResponse cr = codec.normalizeResponse(resp, ctx);
        assertThat(cr.getUsage().getCachedInputTokens()).isEqualTo(80);
    }

    @Test
    void responses_normalizeMessageReasoningContent() {
        OpenAiResponsesCodec codec = new OpenAiResponsesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.OPENAI_RESPONSES, ApiProtocol.OPENAI_RESPONSES, "gpt-4o");

        ObjectNode resp = mapper.createObjectNode();
        resp.put("id", "resp-1");
        resp.put("model", "gpt-4o");
        ObjectNode msgItem = mapper.createObjectNode();
        msgItem.put("type", "message");
        msgItem.put("role", "assistant");
        ObjectNode textPart = mapper.createObjectNode();
        textPart.put("type", "output_text");
        textPart.put("text", "Hello");
        msgItem.putArray("content").add(textPart);
        msgItem.put("reasoning_content", "thinking...");
        resp.putArray("output").add(msgItem);

        CanonicalResponse cr = codec.normalizeResponse(resp, ctx);
        assertThat(cr.getOutputMessages().get(0).getReasoningContent()).isEqualTo("thinking...");

        ObjectNode out = codec.denormalizeResponse(cr, ctx);
        JsonNode firstOut = out.get("output").get(0);
        assertThat(firstOut.get("reasoning_content").asText()).isEqualTo("thinking...");
    }

    // ============== Anthropic Messages ==============

    @Test
    void anthropic_normalizeTopKAndServiceTier() {
        AnthropicMessagesCodec codec = new AnthropicMessagesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "claude-sonnet-4-6");
        body.put("max_tokens", 1024);
        body.put("top_k", 40);
        body.put("service_tier", "standard_only");
        body.put("inference_geo", "us");
        body.put("speed", "fast");
        body.putArray("messages").add(mapper.createObjectNode()
                .put("role", "user")
                .put("content", "Hi"));

        CanonicalRequest req = codec.normalizeRequest(body, ctx);
        assertThat(req.getTopK()).isEqualTo(40);
        assertThat(req.getServiceTier()).isEqualTo("standard_only");
        assertThat(req.getInferenceGeo()).isEqualTo("us");
        assertThat(req.getSpeed()).isEqualTo("fast");
    }

    @Test
    void anthropic_normalizeContainerAndMcp() {
        AnthropicMessagesCodec codec = new AnthropicMessagesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "claude-sonnet-4-6");
        body.put("max_tokens", 1024);
        body.put("container", "container-1");
        ObjectNode mcp = mapper.createObjectNode();
        mcp.put("type", "url");
        mcp.put("url", "https://mcp.example.com");
        mcp.put("name", "my-mcp");
        body.putArray("mcp_servers").add(mcp);
        body.putObject("context_management").put("strategy", "summarize");
        body.putArray("messages").add(mapper.createObjectNode()
                .put("role", "user")
                .put("content", "Hi"));

        CanonicalRequest req = codec.normalizeRequest(body, ctx);
        assertThat(req.getContainer()).isNotNull();
        assertThat(req.getContainer().get("id").asText()).isEqualTo("container-1");
        assertThat(req.getMcpServers()).hasSize(1);
        assertThat(req.getContextManagement()).isNotNull();
    }

    @Test
    void anthropic_normalizeUsageCacheCreationAndReadSeparate() {
        AnthropicMessagesCodec codec = new AnthropicMessagesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        ObjectNode resp = mapper.createObjectNode();
        resp.put("id", "msg-1");
        resp.put("model", "claude-sonnet-4-6");
        resp.putArray("content");
        ObjectNode usage = mapper.createObjectNode();
        usage.put("input_tokens", 100);
        usage.put("output_tokens", 50);
        usage.put("cache_creation_input_tokens", 30);
        usage.put("cache_read_input_tokens", 70);
        ObjectNode stu = mapper.createObjectNode();
        stu.put("web_search_requests", 1);
        usage.set("server_tool_use", stu);
        usage.put("service_tier", "standard");
        resp.set("usage", usage);

        CanonicalResponse cr = codec.normalizeResponse(resp, ctx);
        assertThat(cr.getUsage().getCacheCreationInputTokens()).isEqualTo(30);
        assertThat(cr.getUsage().getCacheReadInputTokens()).isEqualTo(70);
        // Combined cached
        assertThat(cr.getUsage().getCachedInputTokens()).isEqualTo(100);
        assertThat(cr.getUsage().getWebSearchRequests()).isEqualTo(1);
        assertThat(cr.getUsage().getServiceTier()).isEqualTo("standard");
    }

    @Test
    void anthropic_normalizeResponseReasoningContent() {
        AnthropicMessagesCodec codec = new AnthropicMessagesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        ObjectNode resp = mapper.createObjectNode();
        resp.put("id", "msg-1");
        resp.put("model", "claude-sonnet-4-6");
        ObjectNode textBlock = mapper.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", "Hello");
        resp.putArray("content").add(textBlock);
        resp.put("reasoning_content", "thinking details");

        CanonicalResponse cr = codec.normalizeResponse(resp, ctx);
        assertThat(cr.getOutputMessages().get(0).getReasoningContent()).isEqualTo("thinking details");

        ObjectNode out = codec.denormalizeResponse(cr, ctx);
        assertThat(out.get("reasoning_content").asText()).isEqualTo("thinking details");
    }

    @Test
    void anthropic_denormalizeCacheSeparate() {
        AnthropicMessagesCodec codec = new AnthropicMessagesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        ObjectNode resp = mapper.createObjectNode();
        resp.put("id", "msg-1");
        resp.put("model", "claude-sonnet-4-6");
        ObjectNode textBlock = mapper.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", "Hello");
        resp.putArray("content").add(textBlock);
        ObjectNode usage = mapper.createObjectNode();
        usage.put("input_tokens", 100);
        usage.put("output_tokens", 50);
        usage.put("cache_creation_input_tokens", 30);
        usage.put("cache_read_input_tokens", 70);
        resp.set("usage", usage);

        CanonicalResponse cr = codec.normalizeResponse(resp, ctx);
        ObjectNode out = codec.denormalizeResponse(cr, ctx);
        assertThat(out.get("usage").get("cache_creation_input_tokens").asInt()).isEqualTo(30);
        assertThat(out.get("usage").get("cache_read_input_tokens").asInt()).isEqualTo(70);
    }

    @Test
    void anthropic_denormalizeTopKAndContainer() {
        AnthropicMessagesCodec codec = new AnthropicMessagesCodec();
        ProtocolCodec.BridgeContext ctx = new ProtocolCodec.BridgeContext(
                ApiProtocol.ANTHROPIC_MESSAGES, ApiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "claude-sonnet-4-6");
        body.put("max_tokens", 1024);
        body.put("top_k", 40);
        body.put("service_tier", "standard_only");
        body.put("speed", "fast");
        body.putObject("container").put("id", "c-1");
        body.putArray("messages").add(mapper.createObjectNode()
                .put("role", "user")
                .put("content", "Hi"));

        CanonicalRequest req = codec.normalizeRequest(body, ctx);
        ObjectNode out = codec.denormalizeRequest(req, ctx);
        assertThat(out.get("top_k").asInt()).isEqualTo(40);
        assertThat(out.get("service_tier").asText()).isEqualTo("standard_only");
        assertThat(out.get("speed").asText()).isEqualTo("fast");
        assertThat(out.get("container").get("id").asText()).isEqualTo("c-1");
    }

    // ============== StopReasonMapper ==============

    @Test
    void stopReasonMapper_allResponsesStatuses() {
        assertThat(io.github.kongweiguang.llmbridge.core.stream.StopReasonMapper
                .toNormalizedFromResponsesStatus("completed")).isEqualTo("end_turn");
        assertThat(io.github.kongweiguang.llmbridge.core.stream.StopReasonMapper
                .toNormalizedFromResponsesStatus("incomplete")).isEqualTo("max_tokens");
        assertThat(io.github.kongweiguang.llmbridge.core.stream.StopReasonMapper
                .toNormalizedFromResponsesStatus("failed")).isEqualTo("refusal");
        assertThat(io.github.kongweiguang.llmbridge.core.stream.StopReasonMapper
                .toNormalizedFromResponsesStatus("in_progress")).isEqualTo("in_progress");
        assertThat(io.github.kongweiguang.llmbridge.core.stream.StopReasonMapper
                .toNormalizedFromResponsesStatus("queued")).isEqualTo("queued");
        assertThat(io.github.kongweiguang.llmbridge.core.stream.StopReasonMapper
                .toNormalizedFromResponsesStatus("cancelled")).isEqualTo("cancelled");

        assertThat(io.github.kongweiguang.llmbridge.core.stream.StopReasonMapper
                .toResponsesStatus("in_progress")).isEqualTo("in_progress");
        assertThat(io.github.kongweiguang.llmbridge.core.stream.StopReasonMapper
                .toResponsesStatus("cancelled")).isEqualTo("cancelled");
    }
}
