package dev.suprim.zava.internal.crypto;

import dev.suprim.zava.exception.ZavaCryptoException;
import dev.suprim.zava.internal.session.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Hashing utilities: MD5, UUID generation, PIN encryption.
 *
 * <p>All methods use JDK built-in {@link MessageDigest}.
 */
public final class Hashing {

    private Hashing() {}

    /**
     * MD5 hash of a string, returned as lowercase hex.
     *
     * <p>Equivalent to zca-js {@code cryptojs.MD5(input).toString()}.
     *
     * @param input string to hash
     * @return 32-char lowercase hex MD5 digest
     */
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new ZavaCryptoException("MD5 not available", e);
        }
    }

    /**
     * MD5 hash of a file read in chunks.
     *
     * <p>Equivalent to zca-js {@code getMd5LargeFileObject()} with 2MB chunk size.
     *
     * @param file path to the file
     * @return lowercase hex MD5 digest of the entire file
     */
    public static String md5Chunked(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[Constants.MD5_CHUNK_SIZE];

            try (InputStream is = Files.newInputStream(file)) {
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }

            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new ZavaCryptoException("MD5 not available", e);
        } catch (IOException e) {
            throw new ZavaCryptoException("Failed to read file for MD5: " + file, e);
        }
    }

    /**
     * Generate a Zalo-style UUID.
     *
     * <p>Format: {@code <random-uuid>-<md5(userAgent)>}
     *
     * <p>Equivalent to zca-js {@code generateZaloUUID(userAgent)}.
     *
     * @param userAgent the user agent string
     * @return UUID string in Zalo format
     */
    public static String generateUUID(String userAgent) {
        return UUID.randomUUID().toString() + "-" + md5(userAgent);
    }

    /**
     * Encrypt a PIN using MD5.
     *
     * <p>Equivalent to zca-js {@code encryptPin(pin)}.
     *
     * @param pin the PIN string (typically 4 digits)
     * @return lowercase hex MD5 digest
     */
    public static String encryptPin(String pin) {
        return md5(pin);
    }

    /**
     * Validate a PIN against its MD5 hash.
     *
     * <p>Equivalent to zca-js {@code validatePin(encryptedPin, pin)}.
     *
     * @param encryptedPin the stored MD5 hash
     * @param pin          the PIN to verify
     * @return true if the PIN matches the hash
     */
    public static boolean validatePin(String encryptedPin, String pin) {
        return encryptedPin.equals(md5(pin));
    }

    // ── Shared helper ───────────────────────────────────────────────────

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
