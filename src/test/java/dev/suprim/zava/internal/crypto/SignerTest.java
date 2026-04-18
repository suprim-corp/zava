package dev.suprim.zava.internal.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Signer.
 * Test vectors generated from zca-js getSignKey().
 */
class SignerTest {

    @Test
    @DisplayName("getSignKey for getlogininfo matches zca-js output")
    void getSignKeyLoginInfo() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("computer_name", "Web");
        params.put("imei", "test-imei-123");
        params.put("language", "vi");
        params.put("ts", 1700000000000L);

        String result = Signer.getSignKey("getlogininfo", params);
        assertEquals("591e7b95fed41d1ae80c583a34cd8f8c", result);
    }

    @Test
    @DisplayName("getSignKey for getserverinfo matches zca-js output")
    void getSignKeyServerInfo() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imei", "test-imei-123");
        params.put("type", 30);
        params.put("client_version", 671);
        params.put("computer_name", "Web");

        String result = Signer.getSignKey("getserverinfo", params);
        assertEquals("51fdf9e408b28ac9012cacdd7751ff99", result);
    }

    @Test
    @DisplayName("getSignKey sorts keys alphabetically regardless of insertion order")
    void getSignKeySortsKeys() {
        // Insert in reverse order - should produce same result
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts", 1700000000000L);
        params.put("language", "vi");
        params.put("imei", "test-imei-123");
        params.put("computer_name", "Web");

        String result = Signer.getSignKey("getlogininfo", params);
        assertEquals("591e7b95fed41d1ae80c583a34cd8f8c", result);
    }

    @Test
    @DisplayName("getSignKey with empty params")
    void getSignKeyEmptyParams() {
        String result = Signer.getSignKey("test", Map.of());
        // MD5("zsecuretest")
        assertEquals(Hashing.md5("zsecuretest"), result);
    }

    @Test
    @DisplayName("getSignKey skips null values in params")
    void getSignKeyNullValues() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("a", "hello");
        params.put("b", null);
        params.put("c", "world");

        String result = Signer.getSignKey("test", params);
        // Should be MD5("zsecuretesthelloworld") — "b" skipped
        assertEquals(Hashing.md5("zsecuretesthelloworld"), result);
    }
}
