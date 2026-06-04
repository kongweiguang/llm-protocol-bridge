package io.github.kongweiguang.llmbridge.integration;

import io.github.kongweiguang.llmbridge.core.codec.ProtocolCodecRegistry;
import io.github.kongweiguang.llmbridge.core.config.*;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import io.github.kongweiguang.llmbridge.core.routing.ModelResolver;
import io.github.kongweiguang.llmbridge.core.stream.SseFrame;
import io.github.kongweiguang.llmbridge.core.http.WebClientUpstreamHttpClient;
import io.github.kongweiguang.llmbridge.autoconfigure.LlmBridgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for streaming functionality using MockWebServer.
 * Uses collectList() to verify key content without depending on exact event ordering.
 */
class StreamingIntegrationTest {

    private MockWebServer mockServer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    private LlmBridgeService createService(ProviderKind provider) {
        String baseUrl = mockServer.url("/v1").toString();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        ProviderDefinition pd = new ProviderDefinition();
        pd.setKind(provider);
        pd.getEndpoint().setBaseUrl(baseUrl);
        pd.getAuthentication().setToken("test-key");

        ModelAliasDefinition alias = new ModelAliasDefinition();
        alias.setProviderRef("test-provider");
        alias.setUpstreamModel("test-llm");

        LlmBridgeProperties props = new LlmBridgeProperties();
        props.setProviders(Map.of("test-provider", pd));
        props.setModelAliases(Map.of("test-model", alias));
        props.setRoutes(Map.of());
        StreamConfig stc = new StreamConfig();
        stc.setEnabled(true);
        stc.setIncludeUsage(true);
        props.setStream(stc);
        props.setServer(new ServerConfig());

        ProtocolCodecRegistry reg = new ProtocolCodecRegistry();
        ModelResolver router = new ModelResolver(props);
        WebClient wc = WebClient.builder().build();
        WebClientUpstreamHttpClient client = new WebClientUpstreamHttpClient(wc, mapper, new ServerConfig());
        return new LlmBridgeService(reg, router, client, props);
    }

    private ObjectNode chatReq() {
        ObjectNode r = mapper.createObjectNode();
        r.put("model", "test-model"); r.put("stream", true);
        r.putArray("messages").addObject().put("role", "user").put("content", "hi");
        return r;
    }

    private ObjectNode anthropicReq() {
        ObjectNode r = mapper.createObjectNode();
        r.put("model", "test-model"); r.put("stream", true); r.put("max_tokens", 1024);
        r.putArray("messages").addObject().put("role", "user").put("content", "hi");
        return r;
    }

    private ObjectNode responsesReq() {
        ObjectNode r = mapper.createObjectNode();
        r.put("model", "test-model"); r.put("stream", true); r.put("input", "hi");
        return r;
    }

    private static final String OPENAI_CHAT_SSE =
            "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"m\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}\n\n"
            + "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"m\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n"
            + "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"m\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
            + "data: [DONE]\n\n";

    private static final String OPENAI_CHAT_ALT_USAGE_SSE =
            "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"m\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"武汉是\"},\"finish_reason\":null}]}\n\n"
            + "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"m\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
            + "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"m\",\"choices\":[],\"usage\":{\"input_tokens\":11,\"output_tokens\":7,\"total_tokens\":18}}\n\n"
            + "data: [DONE]\n\n";

    private static final String ANTHROPIC_SSE =
            "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"id\":\"m1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"m\",\"stop_reason\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":0}}}\n\n"
            + "event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n"
            + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n"
            + "event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":0}\n\n"
            + "event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":3}}\n\n"
            + "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n";

    private static final String RESPONSES_SSE =
            "event: response.created\ndata: {\"type\":\"response.created\",\"response\":{\"id\":\"r1\",\"object\":\"response\",\"created_at\":123,\"model\":\"m\",\"status\":\"in_progress\"}}\n\n"
            + "event: response.output_item.added\ndata: {\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":{\"id\":\"msg1\",\"type\":\"message\",\"status\":\"in_progress\",\"role\":\"assistant\",\"content\":[]}}\n\n"
            + "event: response.content_part.added\ndata: {\"type\":\"response.content_part.added\",\"item_id\":\"msg1\",\"output_index\":0,\"content_index\":0,\"part\":{\"type\":\"output_text\",\"text\":\"\",\"annotations\":[]}}\n\n"
            + "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg1\",\"output_index\":0,\"content_index\":0,\"delta\":\"Hey\"}\n\n"
            + "event: response.completed\ndata: {\"type\":\"response.completed\",\"response\":{\"id\":\"r1\",\"object\":\"response\",\"created_at\":123,\"model\":\"m\",\"status\":\"completed\",\"output\":[],\"usage\":{\"input_tokens\":5,\"output_tokens\":3,\"total_tokens\":8}}}\n\n";

    // ===== Same-protocol tests =====

    @Test
    void openAiChat_to_openAiChat_stream() {
        mockServer.enqueue(new MockResponse().setBody(OPENAI_CHAT_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.OPENAI_CHAT_COMPATIBLE)
                .proxyStream(ApiProtocol.OPENAI_CHAT_COMPLETIONS, chatReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyContains(frames, "Hello");
                    assertThat(frames.stream().anyMatch(SseFrame::isDone)).isTrue();
                }).verifyComplete();
    }

    @Test
    void anthropic_to_anthropic_stream() {
        mockServer.enqueue(new MockResponse().setBody(ANTHROPIC_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE)
                .proxyStream(ApiProtocol.ANTHROPIC_MESSAGES, anthropicReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyEventEquals(frames, "message_start");
                    assertAnyEventEquals(frames, "message_stop");
                    assertAnyContains(frames, "Hi");
                    assertAnyContains(frames, "\"input_tokens\":10");
                    assertAnyContains(frames, "\"output_tokens\":3");
                }).verifyComplete();
    }

    @Test
    void responses_to_responses_stream() {
        mockServer.enqueue(new MockResponse().setBody(RESPONSES_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.OPENAI_RESPONSES_COMPATIBLE)
                .proxyStream(ApiProtocol.OPENAI_RESPONSES, responsesReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyEventEquals(frames, "response.created");
                    assertAnyEventEquals(frames, "response.completed");
                    assertAnyContains(frames, "Hey");
                }).verifyComplete();
    }

    // ===== Cross-protocol tests =====

    @Test
    void openAiChat_to_anthropic_stream() {
        mockServer.enqueue(new MockResponse().setBody(OPENAI_CHAT_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.OPENAI_CHAT_COMPATIBLE)
                .proxyStream(ApiProtocol.ANTHROPIC_MESSAGES, anthropicReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyContains(frames, "Hello");
                    assertAnyContains(frames, "\"usage\":{\"input_tokens\":0,\"output_tokens\":0}");
                    assertAnyEventEquals(frames, "message_stop");
                }).verifyComplete();
    }

    @Test
    void openAiChat_to_anthropic_stream_preservesAlternateUsageFieldsWithoutRoleChunk() {
        mockServer.enqueue(new MockResponse().setBody(OPENAI_CHAT_ALT_USAGE_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.OPENAI_CHAT_COMPATIBLE)
                .proxyStream(ApiProtocol.ANTHROPIC_MESSAGES, anthropicReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertThat(frames.get(0).getEvent()).isEqualTo("message_start");
                    assertAnyContains(frames, "武汉是");
                    assertAnyContains(frames, "\"input_tokens\":11");
                    assertAnyContains(frames, "\"output_tokens\":7");
                    assertAnyEventEquals(frames, "message_stop");
                }).verifyComplete();
    }

    @Test
    void anthropic_to_openAiChat_stream() {
        mockServer.enqueue(new MockResponse().setBody(ANTHROPIC_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE)
                .proxyStream(ApiProtocol.OPENAI_CHAT_COMPLETIONS, chatReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyContains(frames, "Hi");
                    assertThat(frames.stream().anyMatch(SseFrame::isDone)).isTrue();
                }).verifyComplete();
    }

    @Test
    void openAiChat_to_responses_stream() {
        mockServer.enqueue(new MockResponse().setBody(OPENAI_CHAT_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.OPENAI_CHAT_COMPATIBLE)
                .proxyStream(ApiProtocol.OPENAI_RESPONSES, responsesReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyContains(frames, "Hello");
                    assertAnyEventEquals(frames, "response.completed");
                }).verifyComplete();
    }

    @Test
    void anthropic_to_responses_stream() {
        mockServer.enqueue(new MockResponse().setBody(ANTHROPIC_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE)
                .proxyStream(ApiProtocol.OPENAI_RESPONSES, responsesReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyContains(frames, "Hi");
                    assertAnyEventEquals(frames, "response.completed");
                }).verifyComplete();
    }

    @Test
    void responses_to_openAiChat_stream() {
        mockServer.enqueue(new MockResponse().setBody(RESPONSES_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.OPENAI_RESPONSES_COMPATIBLE)
                .proxyStream(ApiProtocol.OPENAI_CHAT_COMPLETIONS, chatReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyContains(frames, "Hey");
                    assertThat(frames.stream().anyMatch(SseFrame::isDone)).isTrue();
                }).verifyComplete();
    }

    @Test
    void responses_to_anthropic_stream() {
        mockServer.enqueue(new MockResponse().setBody(RESPONSES_SSE).addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.OPENAI_RESPONSES_COMPATIBLE)
                .proxyStream(ApiProtocol.ANTHROPIC_MESSAGES, anthropicReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyContains(frames, "Hey");
                    assertAnyEventEquals(frames, "message_stop");
                }).verifyComplete();
    }

    // ===== Error tests =====

    @Test
    void stream_anthropicError_returnsErrorEvent() {
        mockServer.enqueue(new MockResponse()
                .setBody("event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"rate limited\"}}\n\n")
                .addHeader("Content-Type", "text/event-stream"));
        StepVerifier.create(createService(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE)
                .proxyStream(ApiProtocol.ANTHROPIC_MESSAGES, anthropicReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyEventEquals(frames, "error");
                    assertAnyContains(frames, "rate limited");
                }).verifyComplete();
    }

    @Test
    void stream_http500_returnsErrorEvent() {
        mockServer.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"error\":{\"message\":\"upstream error\",\"type\":\"api_error\"}}")
                .addHeader("Content-Type", "application/json"));
        StepVerifier.create(createService(ProviderKind.OPENAI_CHAT_COMPATIBLE)
                .proxyStream(ApiProtocol.OPENAI_CHAT_COMPLETIONS, chatReq()).collectList())
                .assertNext(frames -> {
                    assertThat(frames).isNotEmpty();
                    assertAnyContains(frames, "error");
                }).verifyComplete();
    }

    @Test
    void stream_disabled_returnsError() {
        ProviderDefinition pd = new ProviderDefinition();
        pd.setKind(ProviderKind.OPENAI_CHAT_COMPATIBLE);
        pd.getEndpoint().setBaseUrl("https://example.com");
        pd.getAuthentication().setToken("k");

        ModelAliasDefinition alias = new ModelAliasDefinition();
        alias.setProviderRef("p");
        alias.setUpstreamModel("m");

        LlmBridgeProperties props = new LlmBridgeProperties();
        props.setProviders(Map.of("p", pd));
        props.setModelAliases(Map.of("m", alias));
        props.setRoutes(Map.of());
        StreamConfig sc = new StreamConfig(); sc.setEnabled(false); props.setStream(sc);
        props.setServer(new ServerConfig());
        LlmBridgeService svc = new LlmBridgeService(new ProtocolCodecRegistry(),
                new ModelResolver(props),
                new WebClientUpstreamHttpClient(WebClient.builder().build(), mapper, new ServerConfig()),
                props);
        StepVerifier.create(svc.proxyStream(ApiProtocol.OPENAI_CHAT_COMPLETIONS, chatReq()))
                .verifyError();
    }

    // ===== Helpers =====

    private void assertAnyContains(List<SseFrame> frames, String text) {
        assertThat(frames.stream().anyMatch(f -> f.getData() != null && f.getData().contains(text)))
                .as("Expected at least one frame containing '%s'", text).isTrue();
    }

    private void assertAnyEventEquals(List<SseFrame> frames, String event) {
        assertThat(frames.stream().anyMatch(f -> event.equals(f.getEvent())))
                .as("Expected at least one frame with event '%s'", event).isTrue();
    }
}
