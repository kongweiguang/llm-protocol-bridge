package io.github.kongweiguang.llmbridge.core.config;

import java.time.Duration;
import lombok.Data;

/**
 * Streaming configuration for the LLM bridge.
 * Maps to {@code llm.bridge.stream} in YAML.
 */
@Data
public class StreamConfig {

    /** Whether streaming is enabled. */
    private boolean enabled = true;

    /** Whether to include usage in the final stream event. */
    private boolean includeUsage = true;

    /** Whether to pass through unknown SSE events. */
    private boolean passThroughUnknownEvents = false;

    /** Whether to send heartbeat comments to prevent proxy timeouts. */
    private boolean heartbeatEnabled = true;

    /** Interval between heartbeat comments. */
    private Duration heartbeatInterval = Duration.ofSeconds(15);

    /** Whether to buffer tool call arguments incrementally. */
    private boolean bufferToolArguments = true;

    /** Whether to fail on malformed SSE events. */
    private boolean failOnMalformedSse = false;

    /** Maximum size of a single SSE event. */
    private long maxEventSize = 2 * 1024 * 1024; // 2MB
}
