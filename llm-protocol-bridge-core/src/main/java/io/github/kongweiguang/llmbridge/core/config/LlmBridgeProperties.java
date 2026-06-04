package io.github.kongweiguang.llmbridge.core.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * Root configuration properties for the LLM Bridge.
 * Maps to {@code llm.bridge} in YAML.
 */
@Data
public class LlmBridgeProperties {

    /** Whether the bridge is enabled. */
    private boolean enabled = true;

    /** Server configuration. */
    private ServerConfig server = new ServerConfig();

    /** Stream configuration. */
    private StreamConfig stream = new StreamConfig();

    /** Compatibility configuration. */
    private CompatibilityConfig compatibility = new CompatibilityConfig();

    /** Upstream provider definitions. Keyed by provider name. */
    private Map<String, ProviderDefinition> providers = new LinkedHashMap<>();

    /** Model alias definitions. Keyed by alias name (the client-facing model name). */
    private Map<String, ModelAliasDefinition> modelAliases = new LinkedHashMap<>();

    /** Route definitions. Keyed by route name (virtual model name). */
    private Map<String, RouteDefinition> routes = new LinkedHashMap<>();
}
