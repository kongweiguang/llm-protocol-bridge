package io.github.kongweiguang.llmbridge.core.stream;

import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses raw byte chunks into {@link SseFrame} objects.
 * Implements the SSE specification (RFC 8895) with support for multi-line data,
 * event types, IDs, retry values, and comments.
 */
@Slf4j
public class SseFrameParser {

    private final long maxEventSize;

    /**
     * Creates a new SseFrameParser with default settings.
     */
    public SseFrameParser() {
        this.maxEventSize = 2 * 1024 * 1024; // 2MB
    }

    /**
     * Creates a new SseFrameParser with the specified max event size.
     *
     * @param maxEventSize maximum size of a single SSE event in bytes
     */
    public SseFrameParser(long maxEventSize) {
        this.maxEventSize = maxEventSize;
    }

    /**
     * Parses a flux of string chunks into SSE events.
     *
     * @param chunks flux of string chunks (may contain partial or multiple events)
     * @return flux of parsed SSE events
     */
    public Flux<SseFrame> parseStringFlux(Flux<String> chunks) {
        return chunks
                .concatMap(chunk -> Flux.fromIterable(parseChunk(chunk)))
                .filter(event -> event != null);
    }

    /**
     * Parses a flux of byte buffers into SSE events.
     *
     * @param buffers flux of byte buffers
     * @return flux of parsed SSE events
     */
    public Flux<SseFrame> parse(Flux<ByteBuffer> buffers) {
        return buffers
                .map(buf -> StandardCharsets.UTF_8.decode(buf).toString())
                .concatMap(chunk -> Flux.fromIterable(parseChunk(chunk)))
                .filter(event -> event != null);
    }

    // Internal state for incremental parsing
    private StringBuilder currentData = new StringBuilder();
    private String currentEvent = null;
    private String currentId = null;
    private Long currentRetry = null;
    private StringBuilder buffer = new StringBuilder();

    /**
     * Parses a chunk of text and returns any complete SSE events.
     * Handles incremental parsing across chunk boundaries.
     *
     * @param chunk the text chunk
     * @return list of parsed events (may be empty)
     */
    public List<SseFrame> parseChunk(String chunk) {
        List<SseFrame> events = new ArrayList<>();
        buffer.append(chunk);

        int start = 0;
        while (start < buffer.length()) {
            // Look for line endings (\n or \r\n)
            int newlineIdx = buffer.indexOf("\n", start);
            if (newlineIdx == -1) {
                // No complete line yet, keep in buffer
                break;
            }

            String line = buffer.substring(start, newlineIdx);
            // Handle \r\n
            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                line = line.substring(0, line.length() - 1);
            }

            start = newlineIdx + 1;

            if (line.isEmpty()) {
                // Empty line = end of event
                SseFrame event = buildEvent();
                if (event != null) {
                    events.add(event);
                }
                resetState();
            } else if (line.startsWith(":")) {
                // Comment line
                String comment = line.substring(1).trim();
                if (currentData.length() == 0 && currentEvent == null) {
                    // Pure comment event
                    events.add(new SseFrame(comment));
                }
                // Otherwise ignore comment within an event
            } else if (line.startsWith("data:")) {
                String value = line.substring(5).trim();
                if (currentData.length() > 0) {
                    currentData.append("\n");
                }
                currentData.append(value);

                // Check size limit
                if (currentData.length() > maxEventSize) {
                    throw new BridgeException(413, "stream_error",
                            "SSE event exceeds maximum size of " + maxEventSize + " bytes");
                }
            } else if (line.startsWith("event:")) {
                currentEvent = line.substring(6).trim();
            } else if (line.startsWith("id:")) {
                currentId = line.substring(3).trim();
            } else if (line.startsWith("retry:")) {
                try {
                    currentRetry = Long.parseLong(line.substring(6).trim());
                } catch (NumberFormatException e) {
                    log.warn("Invalid retry value: {}", line);
                }
            }
            // Other lines are ignored per SSE spec
        }

        // Keep remaining text in buffer
        if (start > 0) {
            buffer.delete(0, start);
        }

        return events;
    }

    private SseFrame buildEvent() {
        if (currentData.length() == 0 && currentEvent == null && currentId == null) {
            return null;
        }

        SseFrame event = new SseFrame();
        if (currentData.length() > 0) {
            event.setData(currentData.toString());
        }
        event.setEvent(currentEvent);
        event.setId(currentId);
        event.setRetry(currentRetry);
        return event;
    }

    private void resetState() {
        currentData.setLength(0);
        currentEvent = null;
        currentId = null;
        currentRetry = null;
    }

    /**
     * Resets the parser state (call when starting a new stream).
     */
    public void reset() {
        buffer.setLength(0);
        resetState();
    }
}
