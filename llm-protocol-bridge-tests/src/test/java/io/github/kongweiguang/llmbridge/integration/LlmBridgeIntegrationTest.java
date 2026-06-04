package io.github.kongweiguang.llmbridge.integration;

import io.github.kongweiguang.llmbridge.core.codec.ProtocolCodecRegistry;
import io.github.kongweiguang.llmbridge.core.config.*;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import io.github.kongweiguang.llmbridge.core.routing.ModelResolver;
import io.github.kongweiguang.llmbridge.core.http.WebClientUpstreamHttpClient;
import io.github.kongweiguang.llmbridge.autoconfigure.LlmBridgeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the LLM bridge using MockWebServer to simulate upstream providers.
 */
class LlmBridgeIntegrationTest {

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

    private LlmBridgeService createService(ProviderKind kind) {
        String baseUrl = mockServer.url("/v1").toString();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        ProviderDefinition pd = new ProviderDefinition();
        pd.setKind(kind);
        pd.getEndpoint().setBaseUrl(baseUrl);
        pd.getAuthentication().setToken("test-key");

        ModelAliasDefinition alias = new ModelAliasDefinition();
        alias.setProviderRef("test-provider");
        alias.setUpstreamModel("test-model");

        LlmBridgeProperties props = new LlmBridgeProperties();
        props.setProviders(Map.of("test-provider", pd));
        props.setModelAliases(Map.of("test-model", alias));
        props.setRoutes(Map.of());
        props.setServer(new ServerConfig());

        ProtocolCodecRegistry reg = new ProtocolCodecRegistry();
        ModelResolver router = new ModelResolver(props);
        WebClient wc = WebClient.builder().build();
        WebClientUpstreamHttpClient client = new WebClientUpstreamHttpClient(wc, mapper, new ServerConfig());
        return new LlmBridgeService(reg, router, client, props);
    }

    @Test
    void proxyOpenAiChatToOpenAiChat() throws Exception {
        ObjectNode mockResponse = mapper.createObjectNode();
        mockResponse.put("id", "chatcmpl-123");
        mockResponse.put("object", "chat.completion");
        mockResponse.put("created", 1234567890);
        mockResponse.put("model", "test-model");
        var choices = mockResponse.putArray("choices");
        var choice = choices.addObject();
        choice.put("index", 0);
        var message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", "Hello! How can I help?");
        choice.put("finish_reason", "stop");
        var usage = mockResponse.putObject("usage");
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 5);
        usage.put("total_tokens", 15);

        mockServer.enqueue(new MockResponse()
                .setBody(mockResponse.toString())
                .addHeader("Content-Type", "application/json"));

        ObjectNode clientRequest = mapper.createObjectNode();
        clientRequest.put("model", "test-model");
        clientRequest.put("temperature", 0.7);
        clientRequest.put("max_tokens", 1024);
        var messages = clientRequest.putArray("messages");
        var sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", "You are helpful.");
        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Hello");

        StepVerifier.create(createService(ProviderKind.OPENAI_CHAT_COMPATIBLE)
                .proxy(ApiProtocol.OPENAI_CHAT_COMPLETIONS, clientRequest))
                .assertNext(response -> {
                    assertThat(response.get("id").asText()).isEqualTo("chatcmpl-123");
                    assertThat(response.get("object").asText()).isEqualTo("chat.completion");
                    assertThat(response.get("choices")).hasSize(1);
                    assertThat(response.get("choices").get(0).get("message").get("content").asText())
                            .isEqualTo("Hello! How can I help?");
                    assertThat(response.get("usage").get("prompt_tokens").asInt()).isEqualTo(10);
                })
                .verifyComplete();

        RecordedRequest recordedRequest = mockServer.takeRequest();
        JsonNode sentBody = mapper.readTree(recordedRequest.getBody().readUtf8());
        assertThat(sentBody.get("model").asText()).isEqualTo("test-model");
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer test-key");
    }

    @Test
    void proxyOpenAiChatToAnthropic_preservesUsage() {
        ObjectNode mockResponse = mapper.createObjectNode();
        mockResponse.put("id", "chatcmpl-123");
        mockResponse.put("object", "chat.completion");
        mockResponse.put("created", 1234567890);
        mockResponse.put("model", "test-model");
        var choices = mockResponse.putArray("choices");
        var choice = choices.addObject();
        choice.put("index", 0);
        var message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", "Hello!");
        choice.put("finish_reason", "stop");
        var usage = mockResponse.putObject("usage");
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 5);
        usage.put("total_tokens", 15);

        mockServer.enqueue(new MockResponse()
                .setBody(mockResponse.toString())
                .addHeader("Content-Type", "application/json"));

        ObjectNode clientRequest = mapper.createObjectNode();
        clientRequest.put("model", "test-model");
        clientRequest.put("max_tokens", 1024);
        var messages = clientRequest.putArray("messages");
        messages.addObject().put("role", "user").put("content", "Hello");

        StepVerifier.create(createService(ProviderKind.OPENAI_CHAT_COMPATIBLE)
                .proxy(ApiProtocol.ANTHROPIC_MESSAGES, clientRequest))
                .assertNext(response -> {
                    assertThat(response.get("type").asText()).isEqualTo("message");
                    assertThat(response.get("content").get(0).get("text").asText()).isEqualTo("Hello!");
                    assertThat(response.get("usage").get("input_tokens").asInt()).isEqualTo(10);
                    assertThat(response.get("usage").get("output_tokens").asInt()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    void proxyAnthropicToOpenAiChat() throws Exception {
        String baseUrl = mockServer.url("/").toString().replaceAll("/$", "");

        ProviderDefinition pd = new ProviderDefinition();
        pd.setKind(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE);
        pd.getEndpoint().setBaseUrl(baseUrl);
        pd.getAuthentication().setToken("test-key");

        ModelAliasDefinition alias = new ModelAliasDefinition();
        alias.setProviderRef("test-provider");
        alias.setUpstreamModel("claude-sonnet-4-6");

        LlmBridgeProperties props = new LlmBridgeProperties();
        props.setProviders(Map.of("test-provider", pd));
        props.setModelAliases(Map.of("test-claude", alias));
        props.setRoutes(Map.of());
        props.setServer(new ServerConfig());

        ProtocolCodecRegistry reg = new ProtocolCodecRegistry();
        ModelResolver router = new ModelResolver(props);
        WebClient wc = WebClient.builder().build();
        WebClientUpstreamHttpClient client = new WebClientUpstreamHttpClient(wc, mapper, new ServerConfig());
        LlmBridgeService svc = new LlmBridgeService(reg, router, client, props);

        ObjectNode mockResponse = mapper.createObjectNode();
        mockResponse.put("id", "msg-123");
        mockResponse.put("type", "message");
        mockResponse.put("role", "assistant");
        mockResponse.put("model", "claude-sonnet-4-6");
        var content = mockResponse.putArray("content");
        var textBlock = content.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", "Hello from Claude!");
        mockResponse.put("stop_reason", "end_turn");
        var usage = mockResponse.putObject("usage");
        usage.put("input_tokens", 10);
        usage.put("output_tokens", 5);

        mockServer.enqueue(new MockResponse()
                .setBody(mockResponse.toString())
                .addHeader("Content-Type", "application/json"));

        ObjectNode clientRequest = mapper.createObjectNode();
        clientRequest.put("model", "test-claude");
        clientRequest.put("temperature", 0.7);
        clientRequest.put("max_tokens", 1024);
        var messages = clientRequest.putArray("messages");
        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Hello");

        StepVerifier.create(svc.proxy(ApiProtocol.OPENAI_CHAT_COMPLETIONS, clientRequest))
                .assertNext(response -> {
                    assertThat(response.get("object").asText()).isEqualTo("chat.completion");
                    assertThat(response.get("choices")).hasSize(1);
                    assertThat(response.get("choices").get(0).get("message").get("content").asText())
                            .isEqualTo("Hello from Claude!");
                    assertThat(response.get("choices").get(0).get("finish_reason").asText()).isEqualTo("stop");
                })
                .verifyComplete();
    }
}
