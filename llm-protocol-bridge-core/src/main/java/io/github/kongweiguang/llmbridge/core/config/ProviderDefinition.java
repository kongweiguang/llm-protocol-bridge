package io.github.kongweiguang.llmbridge.core.config;

import io.github.kongweiguang.llmbridge.core.format.ProviderKind;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.util.Map;

/**
 * Defines an upstream LLM provider with endpoint, authentication, and default settings.
 * Maps to an entry under {@code llm.bridge.providers} in YAML.
 */
@Data
public class ProviderDefinition {

    /** The upstream provider type. */
    private ProviderKind kind;

    /** Upstream endpoint configuration. */
    private EndpointConfig endpoint = new EndpointConfig();

    /** Authentication configuration. */
    private AuthenticationConfig authentication = new AuthenticationConfig();

    /** Default HTTP headers to send with every request to this provider. */
    private Map<String, String> defaultHeaders;

    /** Default request body fields — only applied when the target body is missing the field. */
    private ObjectNode requestDefaults;

    /** Override request body fields — always applied, overwriting existing values. */
    private ObjectNode requestOverrides;

    @Data
    public static class EndpointConfig {
        private String baseUrl;
    }

    @Data
    public static class AuthenticationConfig {
        private String type;
        private String token;
    }
}
