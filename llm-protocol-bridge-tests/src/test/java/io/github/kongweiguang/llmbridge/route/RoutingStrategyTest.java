package io.github.kongweiguang.llmbridge.route;

import io.github.kongweiguang.llmbridge.core.config.*;
import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import io.github.kongweiguang.llmbridge.core.routing.ModelResolutionResult;
import io.github.kongweiguang.llmbridge.core.routing.ModelResolver;
import io.github.kongweiguang.llmbridge.autoconfigure.LlmBridgeAutoConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ModelResolver with different routing strategies.
 */
class RoutingStrategyTest {

    private ProviderDefinition createProvider() {
        ProviderDefinition pd = new ProviderDefinition();
        pd.setKind(ProviderKind.OPENAI_CHAT_COMPATIBLE);
        pd.getEndpoint().setBaseUrl("https://example.com/v1");
        pd.getAuthentication().setToken("test-key");
        return pd;
    }

    private ModelAliasDefinition createAlias(String upstreamModel) {
        ModelAliasDefinition alias = new ModelAliasDefinition();
        alias.setProviderRef("test-provider");
        alias.setUpstreamModel(upstreamModel);
        return alias;
    }

    private LlmBridgeProperties buildProps(String strategy, List<String> aliasNames) {
        return buildProps(strategy, aliasNames, Map.of());
    }

    private LlmBridgeProperties buildProps(String strategy, List<String> aliasNames, Map<String, Integer> weights) {
        ProviderDefinition pd = createProvider();

        Map<String, ModelAliasDefinition> aliases = new java.util.LinkedHashMap<>();
        for (String name : aliasNames) {
            aliases.put(name, createAlias("model-" + name));
        }

        List<RouteCandidateDefinition> candidates = aliasNames.stream()
                .map(name -> {
                    RouteCandidateDefinition c = new RouteCandidateDefinition();
                    c.setModelRef(name);
                    if (weights.containsKey(name)) {
                        c.setWeight(weights.get(name));
                    }
                    return c;
                })
                .toList();

        RouteDefinition route = new RouteDefinition();
        route.setStrategy(strategy);
        route.setCandidates(candidates);

        LlmBridgeProperties props = new LlmBridgeProperties();
        props.setProviders(Map.of("test-provider", pd));
        props.setModelAliases(aliases);
        props.setRoutes(Map.of("group", route));
        return props;
    }

    @Test
    void failoverStrategy_usesConfigOrder() {
        ModelResolver resolver = new ModelResolver(buildProps("failover", List.of("m1", "m2", "m3")));
        ModelResolutionResult result = resolver.resolve("group");

        assertThat(result.getFallbackCandidates()).hasSize(3);
        assertThat(result.getFallbackCandidates().get(0).getName()).isEqualTo("m1");
        assertThat(result.getFallbackCandidates().get(1).getName()).isEqualTo("m2");
        assertThat(result.getFallbackCandidates().get(2).getName()).isEqualTo("m3");
    }

    @Test
    void priorityStrategy_usesConfigOrder() {
        ModelResolver resolver = new ModelResolver(buildProps("priority", List.of("m1", "m2")));
        ModelResolutionResult result = resolver.resolve("group");

        assertThat(result.getFallbackCandidates()).hasSize(2);
        assertThat(result.getFallbackCandidates().get(0).getName()).isEqualTo("m1");
    }

    @Test
    void weightedStrategy_respectsWeights() {
        // Default weight is 1 for all; weighted random selection should still work
        ModelResolver resolver = new ModelResolver(buildProps("weighted", List.of("m1", "m2")));

        int m1Count = 0;
        for (int i = 0; i < 100; i++) {
            ModelResolutionResult result = resolver.resolve("group");
            if (result.getFallbackCandidates().get(0).getName().equals("m1")) {
                m1Count++;
            }
        }
        // Both have equal weight, so distribution should be roughly 50/50
        assertThat(m1Count).isBetween(20, 80);
    }

    @Test
    void weightedStrategy_usesCandidateWeights() {
        ModelResolver resolver = new ModelResolver(buildProps(
                "weighted",
                List.of("m1", "m2"),
                Map.of("m1", 1, "m2", 100)));

        int m2Count = 0;
        for (int i = 0; i < 100; i++) {
            ModelResolutionResult result = resolver.resolve("group");
            if (result.getFallbackCandidates().get(0).getName().equals("m2")) {
                m2Count++;
            }
        }

        assertThat(m2Count).isGreaterThan(85);
    }

    @Test
    void springBoundProperties_preserveCandidateWeights() {
        LlmBridgeAutoConfiguration.ProviderProperty provider =
                new LlmBridgeAutoConfiguration.ProviderProperty();
        provider.setKind(ProviderKind.OPENAI_CHAT_COMPATIBLE);

        LlmBridgeAutoConfiguration.EndpointProperty endpoint =
                new LlmBridgeAutoConfiguration.EndpointProperty();
        endpoint.setBaseUrl("https://example.com/v1");
        provider.setEndpoint(endpoint);

        LlmBridgeAutoConfiguration.AuthenticationProperty authentication =
                new LlmBridgeAutoConfiguration.AuthenticationProperty();
        authentication.setToken("test-key");
        provider.setAuthentication(authentication);

        LlmBridgeAutoConfiguration.ModelAliasProperty m1 =
                new LlmBridgeAutoConfiguration.ModelAliasProperty();
        m1.setProviderRef("test-provider");
        m1.setUpstreamModel("model-m1");

        LlmBridgeAutoConfiguration.ModelAliasProperty m2 =
                new LlmBridgeAutoConfiguration.ModelAliasProperty();
        m2.setProviderRef("test-provider");
        m2.setUpstreamModel("model-m2");

        LlmBridgeAutoConfiguration.RouteCandidateProperty c1 =
                new LlmBridgeAutoConfiguration.RouteCandidateProperty();
        c1.setModelRef("m1");
        c1.setWeight(3);

        LlmBridgeAutoConfiguration.RouteCandidateProperty c2 =
                new LlmBridgeAutoConfiguration.RouteCandidateProperty();
        c2.setModelRef("m2");
        c2.setWeight(7);

        LlmBridgeAutoConfiguration.RouteProperty route =
                new LlmBridgeAutoConfiguration.RouteProperty();
        route.setStrategy("weighted");
        route.setCandidates(List.of(c1, c2));

        LlmBridgeAutoConfiguration.BridgeProperties springProps =
                new LlmBridgeAutoConfiguration.BridgeProperties();
        springProps.setProviders(Map.of("test-provider", provider));
        springProps.setModelAliases(Map.of("m1", m1, "m2", m2));
        springProps.setRoutes(Map.of("group", route));

        LlmBridgeProperties props = springProps.toLlmBridgeProperties();

        assertThat(props.getRoutes().get("group").getCandidates())
                .extracting(RouteCandidateDefinition::getWeight)
                .containsExactly(3, 7);
    }

    @Test
    void roundRobinStrategy_cyclesThroughCandidates() {
        ModelResolver resolver = new ModelResolver(buildProps("round-robin", List.of("m1", "m2", "m3")));

        assertThat(resolver.resolve("group").getFallbackCandidates().get(0).getName()).isEqualTo("m1");
        assertThat(resolver.resolve("group").getFallbackCandidates().get(0).getName()).isEqualTo("m2");
        assertThat(resolver.resolve("group").getFallbackCandidates().get(0).getName()).isEqualTo("m3");
        assertThat(resolver.resolve("group").getFallbackCandidates().get(0).getName()).isEqualTo("m1");
    }

    @Test
    void directModelMatch_ignoresRoutes() {
        ModelResolver resolver = new ModelResolver(buildProps("failover", List.of("m1")));
        ModelResolutionResult result = resolver.resolve("m1");

        assertThat(result.getModelConfig().getName()).isEqualTo("m1");
        assertThat(result.getFallbackCandidates()).hasSize(1);
    }

    @Test
    void unknownModel_throws404() {
        ModelResolver resolver = new ModelResolver(buildProps("failover", List.of("m1")));
        assertThatThrownBy(() -> resolver.resolve("unknown"))
                .isInstanceOf(BridgeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getAllModelNames_includesRoutes() {
        ModelResolver resolver = new ModelResolver(buildProps("failover", List.of("m1", "m2")));
        var names = resolver.getAllModelNames();

        assertThat(names).contains("m1", "m2", "group");
    }

    @Test
    void emptyModel_throws400() {
        LlmBridgeProperties props = new LlmBridgeProperties();
        props.setProviders(Map.of());
        props.setModelAliases(Map.of());
        props.setRoutes(Map.of());
        ModelResolver resolver = new ModelResolver(props);
        assertThatThrownBy(() -> resolver.resolve(""))
                .isInstanceOf(BridgeException.class);
    }
}
