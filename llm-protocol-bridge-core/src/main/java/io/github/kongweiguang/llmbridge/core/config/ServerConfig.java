package io.github.kongweiguang.llmbridge.core.config;

import java.time.Duration;
import lombok.Data;

/**
 * Server-level configuration for the bridge.
 * Maps to {@code llm.bridge.server} in YAML.
 */
@Data
public class ServerConfig {

    /** Authentication configuration. */
    private AuthConfig auth = new AuthConfig();

    /** Time-to-first-byte timeout for upstream requests. */
    private Duration ttfbTimeout = Duration.ofSeconds(30);

    /** Total request timeout for upstream requests. */
    private Duration requestTimeout = Duration.ofSeconds(120);

    /**
     * Authentication configuration.
     */
    @Data
    public static class AuthConfig {

        /** The bearer token required for API access. Empty/null means no auth. */
        private String token;

        /**
         * Returns true if authentication is enabled (token is non-empty).
         */
        public boolean isEnabled() {
            return token != null && !token.isEmpty();
        }
    }
}
