package dev.suprim.zava.exception;

/**
 * Thrown when authentication fails (cookie login or QR login).
 */
public class ZavaAuthException extends ZavaException {

    public ZavaAuthException(String message) {
        super(message);
    }

    public ZavaAuthException(String message, Integer code) {
        super(message, code);
    }

    public ZavaAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
