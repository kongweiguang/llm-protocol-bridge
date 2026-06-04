package io.github.kongweiguang.llmbridge.core.codec;

import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of {@link ProtocolCodec} instances, keyed by {@link ApiProtocol}.
 * Used to look up the appropriate codec for a given API format.
 */
@Slf4j
public class ProtocolCodecRegistry {

    private final Map<ApiProtocol, ProtocolCodec> codecs = new EnumMap<>(ApiProtocol.class);

    /**
     * Creates a new ProtocolCodecRegistry with the three built-in codecs.
     */
    public ProtocolCodecRegistry() {
        register(new OpenAiChatCompletionsCodec());
        register(new OpenAiResponsesCodec());
        register(new AnthropicMessagesCodec());
    }

    /**
     * Registers a codec for its API format.
     *
     * @param codec the codec to register
     */
    public void register(ProtocolCodec codec) {
        codecs.put(codec.apiProtocol(), codec);
        log.info("codec registered: format={}", codec.apiProtocol());
    }

    /**
     * Returns the codec for the specified API format.
     *
     * @param format the API format
     * @return the codec
     * @throws BridgeException if no codec is registered for the format
     */
    public ProtocolCodec get(ApiProtocol format) {
        ProtocolCodec codec = codecs.get(format);
        if (codec == null) {
            log.error("no codec registered for format: {}", format);
            throw new BridgeException(500, "internal_error",
                    "No codec registered for format: " + format);
        }
        return codec;
    }
}
