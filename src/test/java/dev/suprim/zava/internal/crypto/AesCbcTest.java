package dev.suprim.zava.internal.crypto;

import dev.suprim.zava.exception.ZavaCryptoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AES-CBC encrypt/decrypt.
 * Test vectors generated from zca-js using crypto-js.
 */
class AesCbcTest {

    // ── Session variant (Base64-encoded key) ────────────────────────────

    @Nested
    @DisplayName("Session encrypt/decrypt (Base64 key)")
    class SessionTests {

        // Key: 16 bytes "0123456789abcdef" -> Base64
        private static final String SECRET_KEY = "MDEyMzQ1Njc4OWFiY2RlZg==";

        @Test
        @DisplayName("encodeAES produces correct Base64 ciphertext")
        void encodeAES() {
            String result = AesCbc.encodeAES(SECRET_KEY, "Hello Zava!");
            assertEquals("8yDm6mV4FBeYlxifn4fl3w==", result);
        }

        @Test
        @DisplayName("decodeAES decrypts back to plaintext")
        void decodeAES() {
            String result = AesCbc.decodeAES(SECRET_KEY, "8yDm6mV4FBeYlxifn4fl3w==");
            assertEquals("Hello Zava!", result);
        }

        @Test
        @DisplayName("encodeAES/decodeAES roundtrip with longer JSON data")
        void roundtripLongData() {
            String data = "{\"imei\":\"test\",\"language\":\"vi\",\"ts\":1700000000000}";
            String encrypted = AesCbc.encodeAES(SECRET_KEY, data);
            assertEquals(
                    "a8O+s5ibXklahJ3KJ/0Z1CItHh+fzYEG6O+ChZQNG0pD1vx+Lye5gEJfbYGz92we27p05MSV0YMzccbshRFMGw==",
                    encrypted);

            String decrypted = AesCbc.decodeAES(SECRET_KEY, encrypted);
            assertEquals(data, decrypted);
        }

        @Test
        @DisplayName("decodeAES handles URL-encoded input")
        void decodeAESUrlEncoded() {
            // a8O+s5ib... contains '+' which would be '%2B' when URL-encoded
            String urlEncoded = "a8O%2Bs5ibXklahJ3KJ%2F0Z1CItHh%2BfzYEG6O%2BChZQNG0pD1vx%2BLye5gEJfbYGz92we27p05MSV0YMzccbshRFMGw%3D%3D";
            String decrypted = AesCbc.decodeAES(SECRET_KEY, urlEncoded);
            assertEquals("{\"imei\":\"test\",\"language\":\"vi\",\"ts\":1700000000000}", decrypted);
        }

        @Test
        @DisplayName("decodeAES preserves '+' in Base64 (not converted to space)")
        void decodeAESPreservesPlus() {
            // Input with literal '+' must not be treated as space
            String data = "a8O+s5ibXklahJ3KJ/0Z1CItHh+fzYEG6O+ChZQNG0pD1vx+Lye5gEJfbYGz92we27p05MSV0YMzccbshRFMGw==";
            String decrypted = AesCbc.decodeAES(SECRET_KEY, data);
            assertEquals("{\"imei\":\"test\",\"language\":\"vi\",\"ts\":1700000000000}", decrypted);
        }

        @Test
        @DisplayName("decodeAES throws ZavaCryptoException on wrong key")
        void decodeAESWrongKey() {
            // Different 16-byte key
            String wrongKey = "AAAAAAAAAAAAAAAAAAAAAA=="; // 16 zero bytes Base64
            assertThrows(ZavaCryptoException.class,
                    () -> AesCbc.decodeAES(wrongKey, "8yDm6mV4FBeYlxifn4fl3w=="));
        }
    }

    // ── Login variant (UTF-8 key) ───────────────────────────────────────

    @Nested
    @DisplayName("Login encrypt/decrypt (UTF-8 key)")
    class LoginTests {

        private static final String LOGIN_KEY = "3FC4F0D2AB50057BCE0D90D9187A22B1";

        @Test
        @DisplayName("encodeLogin hex uppercase matches zca-js createZcid output")
        void encodeLoginHexUppercase() {
            String msg = "30,test-imei-123,1700000000000";
            String result = AesCbc.encodeLogin(LOGIN_KEY, msg, true, true);
            assertEquals("4F64BD29470A9DC69C4ABD49735481C384A57BBF296C6539A6B7DEF28CD59BBF", result);
        }

        @Test
        @DisplayName("encodeLogin base64 lowercase matches zca-js output")
        void encodeLoginBase64Lowercase() {
            String msg = "30,test-imei-123,1700000000000";
            String result = AesCbc.encodeLogin(LOGIN_KEY, msg, false, false);
            assertEquals("T2S9KUcKncacSr1Jc1SBw4Sle78pbGU5prfe8ozVm78=", result);
        }

        @Test
        @DisplayName("decodeLogin decrypts login response correctly")
        void decodeLogin() {
            String encrypted = "deaKWki8jrqBdfK13XWH5lr32CYLZhMeFUoQpZ2bWRo=";
            String result = AesCbc.decodeLogin(LOGIN_KEY, encrypted);
            assertEquals("{\"uid\":\"12345\",\"secret\":\"abc\"}", result);
        }

        @Test
        @DisplayName("encodeLogin/decodeLogin roundtrip")
        void roundtrip() {
            String data = "{\"status\":\"ok\",\"data\":{\"uid\":\"999\"}}";
            String encrypted = AesCbc.encodeLogin(LOGIN_KEY, data, false, false);
            String decrypted = AesCbc.decodeLogin(LOGIN_KEY, encrypted);
            assertEquals(data, decrypted);
        }
    }
}
