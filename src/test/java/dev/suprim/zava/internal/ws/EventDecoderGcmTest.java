package dev.suprim.zava.internal.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.suprim.zava.exception.ZavaCryptoException;
import dev.suprim.zava.internal.crypto.AesGcm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional EventDecoder tests for encrypt type 2 (AES-GCM + zlib) and type 3 (AES-GCM only).
 */
class EventDecoderGcmTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String encrypt(byte[] keyBytes, byte[] plaintext) throws Exception {
        byte[] iv = new byte[16];
        byte[] aad = new byte[16];
        new SecureRandom().nextBytes(iv);
        new SecureRandom().nextBytes(aad);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] payload = new byte[16 + 16 + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, 16);
        System.arraycopy(aad, 0, payload, 16, 16);
        System.arraycopy(ciphertext, 0, payload, 32, ciphertext.length);

        return Base64.getEncoder().encodeToString(payload);
    }

    private byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buf);
            out.write(buf, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }

    @Test @DisplayName("decode type 3: AES-GCM only")
    void decodeType3() throws Exception {
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String cipherKey = Base64.getEncoder().encodeToString(keyBytes);

        String json = "{\"msgs\":[{\"content\":\"encrypted\"}]}";
        String encData = encrypt(keyBytes, json.getBytes(StandardCharsets.UTF_8));

        ObjectNode parsed = MAPPER.createObjectNode();
        parsed.put("data", encData);
        parsed.put("encrypt", 3);

        JsonNode result = EventDecoder.decode(parsed, cipherKey);
        assertEquals("encrypted", result.path("msgs").get(0).path("content").asText());
    }

    @Test @DisplayName("decode type 2: AES-GCM + zlib")
    void decodeType2() throws Exception {
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String cipherKey = Base64.getEncoder().encodeToString(keyBytes);

        String json = "{\"msgs\":[{\"content\":\"compressed_encrypted\"}]}";
        byte[] compressed = deflate(json.getBytes(StandardCharsets.UTF_8));
        // type 2: encrypt the compressed bytes, then decrypt gives compressed, then inflate
        String encData = encrypt(keyBytes, compressed);

        ObjectNode parsed = MAPPER.createObjectNode();
        parsed.put("data", encData);
        parsed.put("encrypt", 2);

        JsonNode result = EventDecoder.decode(parsed, cipherKey);
        assertEquals("compressed_encrypted", result.path("msgs").get(0).path("content").asText());
    }
}
