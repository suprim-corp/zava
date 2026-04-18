package dev.suprim.zava.exception;

/**
 * Thrown when a request or WebSocket operation times out.
 */
public class ZavaTimeoutException extends ZavaException {

    public ZavaTimeoutException(String message) {
        super(message);
    }

    public ZavaTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
