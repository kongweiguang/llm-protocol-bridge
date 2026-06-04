package io.github.kongweiguang.llmbridge.core.error;

import io.github.kongweiguang.llmbridge.core.canonical.CanonicalError;

/**
 * Exception thrown when the bridge encounters an error during proxying.
 * Wraps a {@link CanonicalError} for consistent error handling.
 */
public class BridgeException extends RuntimeException {

    private final CanonicalError error;

    public BridgeException(CanonicalError error) {
        super(error.getMessage());
        this.error = error;
    }

    public BridgeException(CanonicalError error, Throwable cause) {
        super(error.getMessage(), cause);
        this.error = error;
    }

    public BridgeException(int status, String type, String message) {
        super(message);
        this.error = new CanonicalError(status, type, message);
    }

    public BridgeException(int status, String type, String message, Throwable cause) {
        super(message, cause);
        this.error = new CanonicalError(status, type, message);
    }

    /**
     * Returns the normalized error details.
     *
     * @return the normalized error
     */
    public CanonicalError getError() {
        return error;
    }
}
