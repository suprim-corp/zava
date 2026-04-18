package dev.suprim.zava.exception;

/**
 * Base exception for all Zava SDK errors.
 */
public class ZavaException extends RuntimeException {

    private final Integer code;

    public ZavaException(String message) {
        this(message, null, null);
    }

    public ZavaException(String message, Integer code) {
        this(message, code, null);
    }

    public ZavaException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public ZavaException(String message, Integer code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Error code from the server, or {@code null} if not applicable.
     */
    public Integer getCode() {
        return code;
    }
}
