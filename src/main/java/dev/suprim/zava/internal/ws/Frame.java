package dev.suprim.zava.internal.ws;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Binary frame codec for the Zalo WebSocket protocol.
 *
 * <p>Frame layout:
 * <pre>
 * Byte 0:     version (uint8, always 1)
 * Byte 1-2:   cmd (uint16, little-endian)
 * Byte 3:     subCmd (int8)
 * Byte 4+:    payload (UTF-8 JSON string)
 * </pre>
 */
public final class Frame {

    public static final int HEADER_SIZE = 4;

    private final int version;
    private final int cmd;
    private final int subCmd;
    private final String payload;

    public Frame(int version, int cmd, int subCmd, String payload) {
        this.version = version;
        this.cmd = cmd;
        this.subCmd = subCmd;
        this.payload = payload;
    }

    public int getVersion() { return version; }
    public int getCmd() { return cmd; }
    public int getSubCmd() { return subCmd; }
    public String getPayload() { return payload; }

    /**
     * Decode a raw binary WebSocket message into a Frame.
     *
     * @param data raw bytes from WebSocket
     * @return decoded Frame
     * @throws IllegalArgumentException if data is too short
     */
    public static Frame decode(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Frame too short: expected at least " + HEADER_SIZE + " bytes, got " + data.length);
        }

        int version = data[0] & 0xFF;

        // cmd is uint16 little-endian at bytes 1-2
        int cmd = (data[1] & 0xFF) | ((data[2] & 0xFF) << 8);

        int subCmd = data[3]; // signed int8

        String payload = "";
        if (data.length > HEADER_SIZE) {
            payload = new String(data, HEADER_SIZE, data.length - HEADER_SIZE, StandardCharsets.UTF_8);
        }

        return new Frame(version, cmd, subCmd, payload);
    }

    /**
     * Encode this frame into a binary WebSocket message.
     *
     * @return raw bytes ready to send
     */
    public byte[] encode() {
        byte[] payloadBytes = payload != null
                ? payload.getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        byte[] data = new byte[HEADER_SIZE + payloadBytes.length];

        data[0] = (byte) (version & 0xFF);

        // cmd as uint16 little-endian
        data[1] = (byte) (cmd & 0xFF);
        data[2] = (byte) ((cmd >> 8) & 0xFF);

        data[3] = (byte) subCmd;

        System.arraycopy(payloadBytes, 0, data, HEADER_SIZE, payloadBytes.length);

        return data;
    }

    @Override
    public String toString() {
        return "Frame{version=" + version + ", cmd=" + cmd + ", subCmd=" + subCmd
                + ", payloadLength=" + (payload != null ? payload.length() : 0) + "}";
    }
}
