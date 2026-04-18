package dev.suprim.zava.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    @Test @DisplayName("ZavaException with message")
    void zavaException() {
        ZavaException e = new ZavaException("fail");
        assertEquals("fail", e.getMessage());
        assertNull(e.getCode());
    }

    @Test @DisplayName("ZavaException with message and cause")
    void zavaExceptionCause() {
        RuntimeException cause = new RuntimeException("root");
        ZavaException e = new ZavaException("fail", cause);
        assertEquals("fail", e.getMessage());
        assertSame(cause, e.getCause());
    }

    @Test @DisplayName("ZavaException with message and code")
    void zavaExceptionCode() {
        ZavaException e = new ZavaException("fail", -123);
        assertEquals(-123, e.getCode());
    }

    @Test @DisplayName("ZavaCryptoException with message")
    void cryptoException() {
        ZavaCryptoException e = new ZavaCryptoException("crypto fail");
        assertEquals("crypto fail", e.getMessage());
    }

    @Test @DisplayName("ZavaCryptoException with cause")
    void cryptoExceptionCause() {
        ZavaCryptoException e = new ZavaCryptoException("fail", new RuntimeException());
        assertNotNull(e.getCause());
    }

    @Test @DisplayName("ZavaAuthException with message")
    void authException() {
        ZavaAuthException e = new ZavaAuthException("auth fail");
        assertEquals("auth fail", e.getMessage());
    }

    @Test @DisplayName("ZavaAuthException with cause")
    void authExceptionCause() {
        ZavaAuthException e = new ZavaAuthException("fail", new RuntimeException());
        assertNotNull(e.getCause());
    }

    @Test @DisplayName("ZavaAuthException with code")
    void authExceptionCode() {
        ZavaAuthException e = new ZavaAuthException("fail", 403);
        assertEquals(403, e.getCode());
    }

    @Test @DisplayName("ZavaException with all args")
    void zavaExceptionAllArgs() {
        RuntimeException cause = new RuntimeException();
        ZavaException e = new ZavaException("msg", 500, cause);
        assertEquals("msg", e.getMessage());
        assertEquals(500, e.getCode());
        assertSame(cause, e.getCause());
    }

    @Test @DisplayName("ZavaTimeoutException with message")
    void timeoutException() {
        ZavaTimeoutException e = new ZavaTimeoutException("timeout");
        assertEquals("timeout", e.getMessage());
    }

    @Test @DisplayName("ZavaTimeoutException with cause")
    void timeoutExceptionCause() {
        ZavaTimeoutException e = new ZavaTimeoutException("timeout", new RuntimeException());
        assertNotNull(e.getCause());
    }
}
