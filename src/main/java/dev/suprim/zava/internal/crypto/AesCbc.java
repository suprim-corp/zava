package dev.suprim.zava.internal.crypto;

import dev.suprim.zava.exception.ZavaCryptoException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-CBC encrypt/decrypt utilities.
 *
 * <p>Two key formats are used throughout the Zalo protocol:
 * <ul>
 *     <li><b>Session</b> — key is Base64-encoded (from server's secretKey)</li>
 *     <li><b>Login</b> — key is a raw UTF-8 string (hex string parsed as UTF-8 bytes)</li>
 * </ul>
 *
 * <p>Both use AES/CBC/PKCS5Padding with a zero IV (16 bytes of 0x00).
 */
public final class AesCbc {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final IvParameterSpec ZERO_IV = new IvParameterSpec(new byte[16]);

    private AesCbc() {}

    // ── Session variant (Base64-encoded key) ────────────────────────────

    /**
     * Encrypt data using the session secret key.
     *
     * <p>Equivalent to zca-js {@code encodeAES(secretKey, data)}:
     * key = Base64.decode(secretKey), IV = zero, output = Base64.
     *
     * @param secretKey Base64-encoded AES key (from login response)
     * @param data      plaintext to encrypt
     * @return Base64-encoded ciphertext
     */
    public static String encodeAES(String secretKey, String data) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ZERO_IV);

            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new ZavaCryptoException("AES-CBC session encrypt failed", e);
        }
    }

    /**
     * Decrypt data using the session secret key.
     *
     * <p>Equivalent to zca-js {@code decodeAES(secretKey, data)}:
     * key = Base64.decode(secretKey), input = URL-decoded then Base64-decoded, IV = zero.
     *
     * @param secretKey Base64-encoded AES key
     * @param data      Base64-encoded ciphertext (may be URL-encoded)
     * @return decrypted plaintext
     */
    public static String decodeAES(String secretKey, String data) {
        try {
            String decoded = percentDecode(data);
            byte[] keyBytes = Base64.getDecoder().decode(secretKey);
            byte[] cipherBytes = Base64.getDecoder().decode(decoded);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ZERO_IV);

            byte[] decrypted = cipher.doFinal(cipherBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ZavaCryptoException("AES-CBC session decrypt failed", e);
        }
    }

    // ── Login variant (UTF-8 key) ───────────────────────────────────────

    /**
     * Encrypt data for login requests.
     *
     * <p>Equivalent to zca-js {@code ParamsEncryptor.encodeAES(key, msg, type, uppercase)}:
     * key = UTF-8 bytes of the key string, IV = zero.
     *
     * @param key       raw key string (UTF-8 bytes used directly as AES key)
     * @param data      plaintext to encrypt
     * @param hex       if true, output as uppercase hex; if false, output as Base64
     * @param uppercase if hex mode, whether to uppercase the result
     * @return encrypted string in the requested format
     */
    public static String encodeLogin(String key, String data, boolean hex, boolean uppercase) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ZERO_IV);

            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            String result;
            if (hex) {
                result = bytesToHex(encrypted);
            } else {
                result = Base64.getEncoder().encodeToString(encrypted);
            }

            return uppercase ? result.toUpperCase() : result;
        } catch (Exception e) {
            throw new ZavaCryptoException("AES-CBC login encrypt failed", e);
        }
    }

    /**
     * Decrypt a login response.
     *
     * <p>Equivalent to zca-js {@code decryptResp(key, data)} / {@code decodeRespAES(key, data)}:
     * key = UTF-8 bytes, input = URL-decoded then Base64-decoded, IV = zero.
     *
     * @param key  raw key string (UTF-8 bytes used directly as AES key)
     * @param data Base64-encoded ciphertext (may be URL-encoded)
     * @return decrypted plaintext
     */
    public static String decodeLogin(String key, String data) {
        try {
            String decoded = percentDecode(data);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = Base64.getDecoder().decode(decoded);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ZERO_IV);

            byte[] decrypted = cipher.doFinal(cipherBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ZavaCryptoException("AES-CBC login decrypt failed", e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Percent-decode a string (equivalent to JS {@code decodeURIComponent}).
     *
     * <p>Unlike Java's {@code URLDecoder.decode()}, this does NOT convert '+' to space.
     * This is critical because Base64 uses '+' as a valid character.
     */
    private static String percentDecode(String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
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

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
