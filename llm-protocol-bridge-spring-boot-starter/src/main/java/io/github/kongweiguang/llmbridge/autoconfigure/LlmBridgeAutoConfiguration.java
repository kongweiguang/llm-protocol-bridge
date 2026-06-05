package io.github.kongweiguang.llmbridge.autoconfigure;

import io.github.kongweiguang.llmbridge.core.http.UpstreamHttpClient;
import io.github.kongweiguang.llmbridge.core.http.WebClientUpstreamHttpClient;
import io.github.kongweiguang.llmbridge.core.codec.ProtocolCodecRegistry;
import io.github.kongweiguang.llmbridge.core.config.*;
import io.github.kongweiguang.llmbridge.core.routing.ModelResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for the LLM Bridge.
 * Registers all necessary beans when {@code llm.bridge.enabled=true}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "llm.bridge", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LlmBridgeAutoConfiguration.BridgeProperties.class)
public class LlmBridgeAutoConfiguration {

    // BridgeProperties is registered via @EnableConfigurationProperties on the class

    @Bean
    @ConditionalOnMissingBean
    public LlmBridgeProperties llmBridgeProperties(BridgeProperties bridgeProperties) {
        return bridgeProperties.toLlmBridgeProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProtocolCodecRegistry codecRegistry() {
        return new ProtocolCodecRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelResolver modelRouter(LlmBridgeProperties properties) {
        return new ModelResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public UpstreamHttpClient upstreamClient(WebClient.Builder webClientBuilder,
                                         ObjectMapper objectMapper,
                                         LlmBridgeProperties properties) {
        WebClient webClient = webClientBuilder.build();
        return new WebClientUpstreamHttpClient(webClient, objectMapper, properties.getServer());
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmBridgeService llmBridgeService(ProtocolCodecRegistry codecRegistry,
                                              ModelResolver modelRouter,
                                              UpstreamHttpClient upstreamClient,
                                              LlmBridgeProperties properties) {
        return new LlmBridgeService(codecRegistry, modelRouter, upstreamClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmBridgeController llmBridgeController(LlmBridgeService service,
                                                     LlmBridgeProperties properties) {
        return new LlmBridgeController(service, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmBridgeAuthWebFilter llmBridgeAuthFilter(LlmBridgeProperties properties) {
        return new LlmBridgeAuthWebFilter(properties);
    }

    // ===== Spring-bound configuration properties =====

    @Data
    @ConfigurationProperties(prefix = "llm.bridge")
    public static class BridgeProperties {
        private boolean enabled = true;
        private ServerProperties server = new ServerProperties();
        private StreamProperties stream = new StreamProperties();
        private CompatibilityProperties compatibility = new CompatibilityProperties();
        private java.util.Map<String, ProviderProperty> providers = new java.util.HashMap<>();
        private java.util.Map<String, ModelAliasProperty> modelAliases = new java.util.HashMap<>();
        private java.util.Map<String, RouteProperty> routes = new java.util.HashMap<>();

        public LlmBridgeProperties toLlmBridgeProperties() {
            LlmBridgeProperties props = new LlmBridgeProperties();
            props.setEnabled(enabled);

            // Server config
            ServerConfig serverConfig = new ServerConfig();
            if (server.getAuthToken() != null && !server.getAuthToken().isEmpty()) {
                ServerConfig.AuthConfig authConfig = new ServerConfig.AuthConfig();
                authConfig.setToken(server.getAuthToken());
                serverConfig.setAuth(authConfig);
            }
            if (server.getTtfbTimeout() != null) {
                serverConfig.setTtfbTimeout(server.getTtfbTimeout());
            }
            if (server.getRequestTimeout() != null) {
                serverConfig.setRequestTimeout(server.getRequestTimeout());
            }
            props.setServer(serverConfig);

            // Stream config
            StreamConfig streamConfig = new StreamConfig();
            streamConfig.setEnabled(stream.isEnabled());
            streamConfig.setIncludeUsage(stream.isIncludeUsage());
            streamConfig.setPassThroughUnknownEvents(stream.isPassThroughUnknownEvents());
            streamConfig.setHeartbeatEnabled(stream.isHeartbeatEnabled());
            streamConfig.setHeartbeatInterval(stream.getHeartbeatInterval());
            streamConfig.setBufferToolArguments(stream.isBufferToolArguments());
            streamConfig.setFailOnMalformedSse(stream.isFailOnMalformedSse());
            streamConfig.setMaxEventSize(stream.getMaxEventSize());
            props.setStream(streamConfig);

            // Providers
            java.util.Map<String, ProviderDefinition> providerDefs = new java.util.LinkedHashMap<>();
            if (providers != null) {
                for (var entry : providers.entrySet()) {
                    providerDefs.put(entry.getKey(), toProviderDefinition(entry.getValue()));
                }
            }
            props.setProviders(providerDefs);

            // Model aliases
            java.util.Map<String, ModelAliasDefinition> aliasDefs = new java.util.LinkedHashMap<>();
            if (modelAliases != null) {
                for (var entry : modelAliases.entrySet()) {
                    aliasDefs.put(entry.getKey(), toModelAliasDefinition(entry.getValue()));
                }
            }
            props.setModelAliases(aliasDefs);

            // Routes
            java.util.Map<String, RouteDefinition> routeDefs = new java.util.LinkedHashMap<>();
            if (routes != null) {
                for (var entry : routes.entrySet()) {
                    routeDefs.put(entry.getKey(), toRouteDefinition(entry.getValue()));
                }
            }
            props.setRoutes(routeDefs);

            // Compatibility config
            CompatibilityConfig compatConfig = new CompatibilityConfig();
            compatConfig.setPreserveUnknownFields(compatibility.isPreserveUnknownFields());
            compatConfig.setUnsupportedMediaPolicy(compatibility.getUnsupportedMediaPolicy());
            compatConfig.setDefaultMaxOutputTokens(compatibility.getDefaultMaxOutputTokens());
            compatConfig.setExposeThinking(compatibility.isExposeThinking());
            compatConfig.setIgnoreInvalidThinkingSignature(compatibility.isIgnoreInvalidThinkingSignature());
            props.setCompatibility(compatConfig);

            return props;
        }

        private ProviderDefinition toProviderDefinition(ProviderProperty p) {
            ProviderDefinition pd = new ProviderDefinition();
            pd.setKind(p.getKind());
            if (p.getEndpoint() != null) {
                ProviderDefinition.EndpointConfig ec = new ProviderDefinition.EndpointConfig();
                ec.setBaseUrl(p.getEndpoint().getBaseUrl());
                pd.setEndpoint(ec);
            }
            if (p.getAuthentication() != null) {
                ProviderDefinition.AuthenticationConfig ac = new ProviderDefinition.AuthenticationConfig();
                ac.setType(p.getAuthentication().getType());
                ac.setToken(p.getAuthentication().getToken());
                pd.setAuthentication(ac);
            }
            pd.setDefaultHeaders(p.getDefaultHeaders());
            pd.setRequestDefaults(p.getRequestDefaults());
            pd.setRequestOverrides(p.getRequestOverrides());
            return pd;
        }

        private ModelAliasDefinition toModelAliasDefinition(ModelAliasProperty a) {
            ModelAliasDefinition ad = new ModelAliasDefinition();
            ad.setProviderRef(a.getProviderRef());
            ad.setUpstreamModel(a.getUpstreamModel());
            ad.setRequestDefaults(a.getRequestDefaults());
            ad.setRequestOverrides(a.getRequestOverrides());
            return ad;
        }

        private RouteDefinition toRouteDefinition(RouteProperty r) {
            RouteDefinition rd = new RouteDefinition();
            rd.setStrategy(r.getStrategy());
            if (r.getCandidates() != null) {
                java.util.List<RouteCandidateDefinition> candidates = new java.util.ArrayList<>();
                for (RouteCandidateProperty cp : r.getCandidates()) {
                    RouteCandidateDefinition cd = new RouteCandidateDefinition();
                    cd.setModelRef(cp.getModelRef());
                    cd.setWeight(cp.getWeight());
                    candidates.add(cd);
                }
                rd.setCandidates(candidates);
            }
            rd.setRequestDefaults(r.getRequestDefaults());
            rd.setRequestOverrides(r.getRequestOverrides());
            return rd;
        }
    }

    @Data
    public static class ServerProperties {
        private String authToken;
        private java.time.Duration ttfbTimeout;
        private java.time.Duration requestTimeout;
    }

    @Data
    public static class StreamProperties {
        private boolean enabled = true;
        private boolean includeUsage = true;
        private boolean passThroughUnknownEvents = false;
        private boolean heartbeatEnabled = true;
        private java.time.Duration heartbeatInterval = java.time.Duration.ofSeconds(15);
        private boolean bufferToolArguments = true;
        private boolean failOnMalformedSse = false;
        private long maxEventSize = 2 * 1024 * 1024;
    }

    @Data
    public static class CompatibilityProperties {
        private boolean preserveUnknownFields = true;
        private String unsupportedMediaPolicy = "downgrade";
        private Integer defaultMaxOutputTokens;
        private boolean exposeThinking = false;
        private boolean ignoreInvalidThinkingSignature = true;
    }

    @Data
    public static class ProviderProperty {
        private io.github.kongweiguang.llmbridge.core.format.ProviderKind kind;
        private EndpointProperty endpoint;
        private AuthenticationProperty authentication;
        private java.util.Map<String, String> defaultHeaders;
        private com.fasterxml.jackson.databind.node.ObjectNode requestDefaults;
        private com.fasterxml.jackson.databind.node.ObjectNode requestOverrides;
    }

    @Data
    public static class EndpointProperty {
        private String baseUrl;
        private java.util.Map<String, String> paths;
    }

    @Data
    public static class AuthenticationProperty {
        private String type;
        private String token;
    }

    @Data
    public static class ModelAliasProperty {
        private String providerRef;
        private String upstreamModel;
        private com.fasterxml.jackson.databind.node.ObjectNode requestDefaults;
        private com.fasterxml.jackson.databind.node.ObjectNode requestOverrides;
    }

    @Data
    public static class RouteProperty {
        private String strategy = "failover";
        private java.util.List<RouteCandidateProperty> candidates = new java.util.ArrayList<>();
        private com.fasterxml.jackson.databind.node.ObjectNode requestDefaults;
        private com.fasterxml.jackson.databind.node.ObjectNode requestOverrides;
    }

    @Data
    public static class RouteCandidateProperty {
        private String modelRef;
        private int weight = 1;
    }
}
