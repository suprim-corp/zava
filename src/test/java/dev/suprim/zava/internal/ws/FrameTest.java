package dev.suprim.zava.internal.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrameTest {

    @Test
    @DisplayName("encode and decode roundtrip")
    void roundtrip() {
        Frame original = new Frame(1, 501, 0, "{\"msg\":\"hello\"}");
        byte[] encoded = original.encode();
        Frame decoded = Frame.decode(encoded);

        assertEquals(1, decoded.getVersion());
        assertEquals(501, decoded.getCmd());
        assertEquals(0, decoded.getSubCmd());
        assertEquals("{\"msg\":\"hello\"}", decoded.getPayload());
    }

    @Test
    @DisplayName("decode reads cmd as uint16 little-endian")
    void decodeLittleEndian() {
        // cmd = 3000 = 0x0BB8
        // little-endian: byte1 = 0xB8, byte2 = 0x0B
        byte[] data = new byte[]{1, (byte) 0xB8, 0x0B, 0};
        Frame frame = Frame.decode(data);

        assertEquals(1, frame.getVersion());
        assertEquals(3000, frame.getCmd());
        assertEquals(0, frame.getSubCmd());
        assertEquals("", frame.getPayload());
    }

    @Test
    @DisplayName("encode writes cmd as uint16 little-endian")
    void encodeLittleEndian() {
        Frame frame = new Frame(1, 3000, 0, "");
        byte[] data = frame.encode();

        assertEquals(1, data[0]);
        assertEquals((byte) 0xB8, data[1]); // low byte
        assertEquals((byte) 0x0B, data[2]); // high byte
        assertEquals(0, data[3]);
    }

    @Test
    @DisplayName("decode cipher key frame (cmd=1, subCmd=1)")
    void decodeCipherKeyFrame() {
        String payload = "{\"key\":\"abc123base64key==\"}";
        Frame original = new Frame(1, 1, 1, payload);
        byte[] encoded = original.encode();
        Frame decoded = Frame.decode(encoded);

        assertEquals(1, decoded.getCmd());
        assertEquals(1, decoded.getSubCmd());
        assertEquals(payload, decoded.getPayload());
    }

    @Test
    @DisplayName("decode ping frame (cmd=2, subCmd=1)")
    void decodePingFrame() {
        Frame frame = new Frame(1, 2, 1, "{\"eventId\":1700000000000}");
        byte[] encoded = frame.encode();
        Frame decoded = Frame.decode(encoded);

        assertEquals(2, decoded.getCmd());
        assertEquals(1, decoded.getSubCmd());
    }

    @Test
    @DisplayName("decode throws on too-short data")
    void decodeTooShort() {
        assertThrows(IllegalArgumentException.class, () -> Frame.decode(new byte[3]));
    }

    @Test
    @DisplayName("decode handles empty payload")
    void decodeEmptyPayload() {
        byte[] data = new byte[]{1, (byte) 0xF5, 0x01, 0}; // cmd=501
        Frame frame = Frame.decode(data);
        assertEquals("", frame.getPayload());
    }

    @Test
    @DisplayName("encode/decode with negative subCmd")
    void negativeSubCmd() {
        Frame frame = new Frame(1, 501, -1, "test");
        byte[] encoded = frame.encode();
        Frame decoded = Frame.decode(encoded);
        assertEquals(-1, decoded.getSubCmd());
    }
}
