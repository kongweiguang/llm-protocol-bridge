package io.github.kongweiguang.llmbridge.core.stream;

import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Formats {@link SseFrame} objects into SSE text format.
 * Implements the SSE specification (RFC 8895).
 */
public class SseFrameWriter {

    /**
     * Formats a single SSE event into text.
     *
     * @param event the SSE event to format
     * @return the formatted SSE text (includes trailing blank line)
     */
    public String format(SseFrame event) {
        StringBuilder sb = new StringBuilder();

        // Comment
        if (event.getComment() != null && !event.getComment().isEmpty()) {
            sb.append(": ").append(event.getComment()).append("\n\n");
            return sb.toString();
        }

        // ID
        if (event.getId() != null) {
            sb.append("id: ").append(event.getId()).append("\n");
        }

        // Event type
        if (event.getEvent() != null && !event.getEvent().isEmpty()) {
            sb.append("event: ").append(event.getEvent()).append("\n");
        }

        // Retry
        if (event.getRetry() != null) {
            sb.append("retry: ").append(event.getRetry()).append("\n");
        }

        // Data
        if (event.getData() != null) {
            // Handle multi-line data
            String[] lines = event.getData().split("\n", -1);
            for (String line : lines) {
                sb.append("data: ").append(line).append("\n");
            }
        }

        // Blank line to end the event
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Formats a flux of SSE events into a flux of strings.
     *
     * @param events flux of SSE events
     * @return flux of formatted SSE text strings
     */
    public Flux<String> formatToString(Flux<SseFrame> events) {
        return events.map(this::format);
    }

    /**
     * Formats a flux of SSE events into a flux of byte buffers.
     *
     * @param events  flux of SSE events
     * @param factory function to create byte buffers from strings
     * @return flux of byte buffers containing formatted SSE text
     */
    public Flux<ByteBuffer> formatToByteBuffer(Flux<SseFrame> events) {
        return events
                .map(this::format)
                .map(s -> ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Formats a flux of SSE events into a flux of byte arrays.
     *
     * @param events flux of SSE events
     * @return flux of byte arrays containing formatted SSE text
     */
    public Flux<byte[]> formatToBytes(Flux<SseFrame> events) {
        return events
                .map(this::format)
                .map(s -> s.getBytes(StandardCharsets.UTF_8));
    }
}
