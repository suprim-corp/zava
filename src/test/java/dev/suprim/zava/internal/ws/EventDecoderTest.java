package dev.suprim.zava.internal.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.suprim.zava.exception.ZavaCryptoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

class EventDecoderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("decode type 0: plain JSON")
    void decodeTypePlain() throws Exception {
        ObjectNode parsed = MAPPER.createObjectNode();
        parsed.put("data", "{\"msgs\":[{\"content\":\"hello\"}]}");
        parsed.put("encrypt", 0);

        JsonNode result = EventDecoder.decode(parsed, null);
        assertEquals("hello", result.path("msgs").get(0).path("content").asText());
    }

    @Test
    @DisplayName("decode type 1: zlib compressed")
    void decodeTypeZlib() throws Exception {
        String json = "{\"msgs\":[{\"content\":\"compressed\"}]}";
        byte[] compressed = deflate(json.getBytes());
        String b64 = Base64.getEncoder().encodeToString(compressed);

        ObjectNode parsed = MAPPER.createObjectNode();
        parsed.put("data", b64);
        parsed.put("encrypt", 1);

        JsonNode result = EventDecoder.decode(parsed, null);
        assertEquals("compressed", result.path("msgs").get(0).path("content").asText());
    }

    @Test
    @DisplayName("decode type 0: handles empty data")
    void decodeEmptyData() throws Exception {
        ObjectNode parsed = MAPPER.createObjectNode();
        parsed.put("data", "");
        parsed.put("encrypt", 0);

        JsonNode result = EventDecoder.decode(parsed, null);
        assertTrue(result.isObject());
    }

    @Test
    @DisplayName("decode throws on type 2 without cipher key")
    void decodeType2NoCipherKey() {
        ObjectNode parsed = MAPPER.createObjectNode();
        parsed.put("data", "somedata");
        parsed.put("encrypt", 2);

        assertThrows(ZavaCryptoException.class, () -> EventDecoder.decode(parsed, null));
    }

    @Test
    @DisplayName("decode throws on type 3 without cipher key")
    void decodeType3NoCipherKey() {
        ObjectNode parsed = MAPPER.createObjectNode();
        parsed.put("data", "somedata");
        parsed.put("encrypt", 3);

        assertThrows(ZavaCryptoException.class, () -> EventDecoder.decode(parsed, null));
    }

    @Test
    @DisplayName("decode throws on unknown encrypt type")
    void decodeUnknownType() {
        ObjectNode parsed = MAPPER.createObjectNode();
        parsed.put("data", "test");
        parsed.put("encrypt", 5);

        assertThrows(ZavaCryptoException.class, () -> EventDecoder.decode(parsed, null));
    }

    @Test
    @DisplayName("inflate decompresses zlib data")
    void inflate() {
        String original = "Hello Zava WebSocket!";
        byte[] compressed = deflate(original.getBytes());
        byte[] decompressed = EventDecoder.inflate(compressed);
        assertEquals(original, new String(decompressed));
    }

    // Helper: compress with zlib (raw deflate, no gzip wrapper)
    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }
}
