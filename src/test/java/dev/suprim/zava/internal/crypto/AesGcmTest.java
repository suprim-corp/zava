package dev.suprim.zava.internal.crypto;

import dev.suprim.zava.exception.ZavaCryptoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AES-GCM decrypt.
 * Since zca-js only decrypts (server sends encrypted data), we create test data
 * by encrypting with Java's AES-GCM and verifying our decrypt method works.
 */
class AesGcmTest {

    @Test
    @DisplayName("decrypt correctly decrypts AES-GCM payload with AAD")
    void decrypt() throws Exception {
        // Generate a 128-bit key
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String cipherKey = Base64.getEncoder().encodeToString(keyBytes);

        // Generate IV and AAD (each 16 bytes)
        byte[] iv = new byte[16];
        byte[] aad = new byte[16];
        new SecureRandom().nextBytes(iv);
        new SecureRandom().nextBytes(aad);

        // Encrypt test data
        String plaintext = "{\"cmd\":501,\"data\":{\"msg\":\"hello\"}}";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Build payload: [IV 16B][AAD 16B][ciphertext+tag]
        byte[] payload = new byte[16 + 16 + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, 16);
        System.arraycopy(aad, 0, payload, 16, 16);
        System.arraycopy(ciphertext, 0, payload, 32, ciphertext.length);

        // Decrypt
        String result = AesGcm.decrypt(cipherKey, payload);
        assertEquals(plaintext, result);
    }

    @Test
    @DisplayName("decrypt throws on payload too short")
    void decryptPayloadTooShort() {
        String key = Base64.getEncoder().encodeToString(new byte[16]);
        byte[] shortPayload = new byte[40]; // < 48 minimum
        assertThrows(ZavaCryptoException.class, () -> AesGcm.decrypt(key, shortPayload));
    }

    @Test
    @DisplayName("decrypt throws on wrong key")
    void decryptWrongKey() throws Exception {
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);

        byte[] iv = new byte[16];
        byte[] aad = new byte[16];
        new SecureRandom().nextBytes(iv);
        new SecureRandom().nextBytes(aad);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        byte[] ciphertext = cipher.doFinal("test data".getBytes(StandardCharsets.UTF_8));

        byte[] payload = new byte[16 + 16 + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, 16);
        System.arraycopy(aad, 0, payload, 16, 16);
        System.arraycopy(ciphertext, 0, payload, 32, ciphertext.length);

        // Use a completely different key
        byte[] wrongKeyBytes = new byte[16];
        new SecureRandom().nextBytes(wrongKeyBytes);
        String wrongKey = Base64.getEncoder().encodeToString(wrongKeyBytes);

        assertThrows(ZavaCryptoException.class, () -> AesGcm.decrypt(wrongKey, payload));
    }

    @Test
    @DisplayName("decrypt throws on tampered ciphertext")
    void decryptTamperedData() throws Exception {
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String cipherKey = Base64.getEncoder().encodeToString(keyBytes);

        byte[] iv = new byte[16];
        byte[] aad = new byte[16];
        new SecureRandom().nextBytes(iv);
        new SecureRandom().nextBytes(aad);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        byte[] ciphertext = cipher.doFinal("test".getBytes(StandardCharsets.UTF_8));

        byte[] payload = new byte[16 + 16 + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, 16);
        System.arraycopy(aad, 0, payload, 16, 16);
        System.arraycopy(ciphertext, 0, payload, 32, ciphertext.length);

        // Tamper with ciphertext
        payload[33] ^= 0xFF;

        assertThrows(ZavaCryptoException.class, () -> AesGcm.decrypt(cipherKey, payload));
    }

    @Test
    @DisplayName("decrypt handles empty plaintext")
    void decryptEmptyPlaintext() throws Exception {
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String cipherKey = Base64.getEncoder().encodeToString(keyBytes);

        byte[] iv = new byte[16];
        byte[] aad = new byte[16];
        new SecureRandom().nextBytes(iv);
        new SecureRandom().nextBytes(aad);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        byte[] ciphertext = cipher.doFinal(new byte[0]);

        byte[] payload = new byte[16 + 16 + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, 16);
        System.arraycopy(aad, 0, payload, 16, 16);
        System.arraycopy(ciphertext, 0, payload, 32, ciphertext.length);

        String result = AesGcm.decrypt(cipherKey, payload);
        assertEquals("", result);
    }
}
