package dev.suprim.zava.exception;

/**
 * Thrown when an encryption or decryption operation fails.
 */
public class ZavaCryptoException extends ZavaException {

    public ZavaCryptoException(String message) {
        super(message);
    }

    public ZavaCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
