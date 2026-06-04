package io.github.kongweiguang.llmbridge.core.stream;

import lombok.Data;

import java.util.Map;

/**
 * Represents a Server-Sent Event (SSE).
 * Supports all SSE fields: event, id, data, retry, and comments.
 */
@Data
public class SseFrame {

    /** SSE event type (e.g., "message_start", "content_block_delta"). */
    private String event;

    /** SSE event ID. */
    private String id;

    /** SSE data payload (typically JSON or [DONE]). */
    private String data;

    /** SSE retry interval in milliseconds. */
    private Long retry;

    /** SSE comment (used for heartbeat pings). */
    private String comment;

    /** Extension fields. */
    private Map<String, String> extensions;

    public SseFrame() {
    }

    public SseFrame(String event, String data) {
        this.event = event;
        this.data = data;
    }

    public SseFrame(String comment) {
        this.comment = comment;
    }

    /**
     * Returns true if this is a [DONE] event.
     */
    public boolean isDone() {
        return "[DONE]".equals(data);
    }

    /**
     * Returns true if this is a comment (heartbeat).
     */
    public boolean isComment() {
        return comment != null && !comment.isEmpty();
    }
}
