package dev.suprim.zava.internal.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.exception.ZavaCryptoException;
import dev.suprim.zava.internal.crypto.AesGcm;
import dev.suprim.zava.internal.http.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Inflater;

/**
 * Decodes WebSocket event payloads.
 *
 * <p>4 encryption modes based on the {@code encrypt} field:
 * <ul>
 *   <li><b>0</b> — Plain JSON (no encryption, no compression)</li>
 *   <li><b>1</b> — zlib-compressed (Base64 → inflate)</li>
 *   <li><b>2</b> — AES-GCM + zlib (Base64 → decrypt → inflate)</li>
 *   <li><b>3</b> — AES-GCM only (Base64 → decrypt)</li>
 * </ul>
 *
 * <p>Equivalent to zca-js {@code decodeEventData()}.
 */
public final class EventDecoder {

    private static final Logger log = LoggerFactory.getLogger(EventDecoder.class);
    private static final ObjectMapper MAPPER = ResponseHandler.mapper();

    private EventDecoder() {}

    /**
     * Decode an event payload from a parsed WebSocket frame.
     *
     * @param parsed    the parsed JSON object containing {@code data} and {@code encrypt} fields
     * @param cipherKey the AES-GCM cipher key (received during key exchange), may be null for modes 0/1
     * @return decoded JSON tree
     */
    public static JsonNode decode(JsonNode parsed, String cipherKey) {
        String rawData = parsed.path("data").asText(null);
        int encryptType = parsed.path("encrypt").asInt(0);

        if (rawData == null || rawData.isEmpty()) {
            return MAPPER.createObjectNode();
        }

        try {
            switch (encryptType) {
                case 0:
                    // Plain JSON
                    return MAPPER.readTree(rawData);

                case 1: {
                    // zlib compressed: Base64 → inflate
                    byte[] compressed = Base64.getDecoder().decode(rawData);
                    byte[] decompressed = inflate(compressed);
                    return MAPPER.readTree(new String(decompressed, StandardCharsets.UTF_8));
                }

                case 2: {
                    // AES-GCM + zlib: URL-decode → Base64 → decrypt → inflate
                    if (cipherKey == null) {
                        throw new ZavaCryptoException("Cipher key required for encrypt type 2");
                    }
                    String urlDecoded = percentDecode(rawData);
                    byte[] decoded = Base64.getDecoder().decode(urlDecoded);
                    String decrypted = AesGcm.decrypt(cipherKey, decoded);
                    byte[] decompressed = inflate(decrypted.getBytes(StandardCharsets.UTF_8));
                    return MAPPER.readTree(new String(decompressed, StandardCharsets.UTF_8));
                }

                case 3: {
                    // AES-GCM only: URL-decode → Base64 → decrypt
                    if (cipherKey == null) {
                        throw new ZavaCryptoException("Cipher key required for encrypt type 3");
                    }
                    String urlDecoded = percentDecode(rawData);
                    byte[] decoded = Base64.getDecoder().decode(urlDecoded);
                    String decrypted = AesGcm.decrypt(cipherKey, decoded);
                    return MAPPER.readTree(decrypted);
                }

                default:
                    throw new ZavaCryptoException("Unknown encrypt type: " + encryptType);
            }
        } catch (ZavaCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaCryptoException("Failed to decode event data (type=" + encryptType + ")", e);
        }
    }

    /**
     * Decompress zlib (raw inflate, no gzip header).
     */
    static byte[] inflate(byte[] data) {
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(data);

            byte[] buffer = new byte[4096];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(data.length * 2);

            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) break;
                out.write(buffer, 0, count);
            }
            inflater.end();

            return out.toByteArray();
        } catch (Exception e) {
            throw new ZavaCryptoException("zlib inflate failed", e);
        }
    }

    private static String percentDecode(String s) {
        if (s.indexOf('%') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    sb.append((char) ((hi << 4) | lo));
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
