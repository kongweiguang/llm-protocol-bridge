package io.github.kongweiguang.llmbridge.route;

import io.github.kongweiguang.llmbridge.core.config.*;
import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import io.github.kongweiguang.llmbridge.core.routing.ModelResolver;
import io.github.kongweiguang.llmbridge.core.routing.ModelResolutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ModelResolver}.
 */
class ModelRouterTest {

    private ModelResolver router;

    @BeforeEach
    void setUp() {
        ProviderDefinition openaiProvider = new ProviderDefinition();
        openaiProvider.setKind(ProviderKind.OPENAI_CHAT_COMPATIBLE);
        openaiProvider.getEndpoint().setBaseUrl("https://api.openai.com/v1");
        openaiProvider.getAuthentication().setToken("sk-test");

        ProviderDefinition anthropicProvider = new ProviderDefinition();
        anthropicProvider.setKind(ProviderKind.ANTHROPIC_MESSAGES_COMPATIBLE);
        anthropicProvider.getEndpoint().setBaseUrl("https://api.anthropic.com");
        anthropicProvider.getAuthentication().setToken("sk-ant");

        ProviderDefinition glmProvider = new ProviderDefinition();
        glmProvider.setKind(ProviderKind.OPENAI_CHAT_COMPATIBLE);
        glmProvider.getEndpoint().setBaseUrl("https://open.bigmodel.cn/api/paas/v4");
        glmProvider.getAuthentication().setToken("sk-glm");

        ModelAliasDefinition gptAlias = new ModelAliasDefinition();
        gptAlias.setProviderRef("openai");
        gptAlias.setUpstreamModel("gpt-4");

        ModelAliasDefinition claudeAlias = new ModelAliasDefinition();
        claudeAlias.setProviderRef("anthropic");
        claudeAlias.setUpstreamModel("claude-sonnet-4-6");

        ModelAliasDefinition glmAlias = new ModelAliasDefinition();
        glmAlias.setProviderRef("glm");
        glmAlias.setUpstreamModel("glm-4.7");

        RouteCandidateDefinition c1 = new RouteCandidateDefinition();
        c1.setModelRef("gpt-main");
        RouteCandidateDefinition c2 = new RouteCandidateDefinition();
        c2.setModelRef("claude-main");
        RouteCandidateDefinition c3 = new RouteCandidateDefinition();
        c3.setModelRef("glm-chat");

        RouteDefinition route = new RouteDefinition();
        route.setStrategy("failover");
        route.setCandidates(List.of(c1, c2, c3));

        LlmBridgeProperties properties = new LlmBridgeProperties();
        properties.setProviders(Map.of("openai", openaiProvider, "anthropic", anthropicProvider, "glm", glmProvider));
        properties.setModelAliases(Map.of("gpt-main", gptAlias, "claude-main", claudeAlias, "glm-chat", glmAlias));
        properties.setRoutes(Map.of("coding", route));

        router = new ModelResolver(properties);
    }

    @Test
    void resolveDirectModel() {
        ModelResolutionResult result = router.resolve("gpt-main");
        assertThat(result.getRequestedModel()).isEqualTo("gpt-main");
        assertThat(result.getModelConfig().getName()).isEqualTo("gpt-main");
        assertThat(result.getModelConfig().getModel()).isEqualTo("gpt-4");
        assertThat(result.getFallbackCandidates()).hasSize(1);
    }

    @Test
    void resolveFallbackGroup() {
        ModelResolutionResult result = router.resolve("coding");
        assertThat(result.getRequestedModel()).isEqualTo("coding");
        assertThat(result.getModelConfig().getName()).isEqualTo("gpt-main");
        assertThat(result.getFallbackCandidates()).hasSize(3);
        assertThat(result.getFallbackCandidates().get(0).getName()).isEqualTo("gpt-main");
        assertThat(result.getFallbackCandidates().get(1).getName()).isEqualTo("claude-main");
        assertThat(result.getFallbackCandidates().get(2).getName()).isEqualTo("glm-chat");
    }

    @Test
    void resolveUnknownModelThrows() {
        assertThatThrownBy(() -> router.resolve("unknown-model"))
                .isInstanceOf(BridgeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void resolveNullModelThrows() {
        assertThatThrownBy(() -> router.resolve(null))
                .isInstanceOf(BridgeException.class)
                .hasMessageContaining("model is required");
    }

    @Test
    void resolveEmptyModelThrows() {
        assertThatThrownBy(() -> router.resolve(""))
                .isInstanceOf(BridgeException.class)
                .hasMessageContaining("model is required");
    }
}
