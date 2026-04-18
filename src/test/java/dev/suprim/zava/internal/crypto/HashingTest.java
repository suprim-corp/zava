package dev.suprim.zava.internal.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Hashing utilities.
 * MD5 test vectors verified against zca-js (crypto-js + node:crypto).
 */
class HashingTest {

    @Test
    @DisplayName("md5 of empty string")
    void md5Empty() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Hashing.md5(""));
    }

    @Test
    @DisplayName("md5 of 'hello'")
    void md5Hello() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", Hashing.md5("hello"));
    }

    @Test
    @DisplayName("md5 of 'zsecure'")
    void md5Zsecure() {
        assertEquals("76b3c81a371e56e00c11d13d4bbd85c6", Hashing.md5("zsecure"));
    }

    @Test
    @DisplayName("md5Chunked produces same result as md5 for small files")
    void md5Chunked(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");
        assertEquals(Hashing.md5("hello"), Hashing.md5Chunked(file));
    }

    @Test
    @DisplayName("md5Chunked handles file larger than chunk size boundary")
    void md5ChunkedLargeFile(@TempDir Path tempDir) throws IOException {
        // Create a file with known repeating content
        Path file = tempDir.resolve("large.bin");
        byte[] data = new byte[4 * 1024 * 1024]; // 4MB = 2 chunks
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(file, data);

        String hash = Hashing.md5Chunked(file);
        assertNotNull(hash);
        assertEquals(32, hash.length());

        // Verify it's the same when read again
        assertEquals(hash, Hashing.md5Chunked(file));
    }

    @Test
    @DisplayName("generateUUID has correct format: uuid-md5")
    void generateUUID() {
        String result = Hashing.generateUUID("Mozilla/5.0 Test");
        assertNotNull(result);
        // Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx-<32 hex chars>
        assertTrue(result.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[0-9a-f]{32}"));

        // MD5 suffix should be deterministic for the same user agent
        String md5Part = result.substring(result.lastIndexOf('-') + 1);
        assertEquals(Hashing.md5("Mozilla/5.0 Test"), md5Part);
    }

    @Test
    @DisplayName("encryptPin matches zca-js output")
    void encryptPin() {
        assertEquals("81dc9bdb52d04dc20036dbd8313ed055", Hashing.encryptPin("1234"));
        assertEquals("4a7d1ed414474e4033ac29ccb8653d9b", Hashing.encryptPin("0000"));
    }

    @Test
    @DisplayName("validatePin returns true for matching pin")
    void validatePinMatch() {
        assertTrue(Hashing.validatePin("81dc9bdb52d04dc20036dbd8313ed055", "1234"));
    }

    @Test
    @DisplayName("validatePin returns false for wrong pin")
    void validatePinNoMatch() {
        assertFalse(Hashing.validatePin("81dc9bdb52d04dc20036dbd8313ed055", "5678"));
    }
}
