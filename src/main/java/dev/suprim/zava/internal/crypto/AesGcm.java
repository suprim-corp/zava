package dev.suprim.zava.internal.crypto;

import dev.suprim.zava.exception.ZavaCryptoException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-GCM decrypt for WebSocket event payloads.
 *
 * <p>The raw payload layout (after Base64 decoding) is:
 * <pre>
 *   [IV: 16 bytes][AAD: 16 bytes][ciphertext + auth tag: remaining bytes]
 * </pre>
 *
 * <p>Equivalent to zca-js {@code decodeEventData()} with encrypt type 2 or 3.
 */
public final class AesGcm {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 16;
    private static final int AAD_LENGTH = 16;
    private static final int TAG_BITS = 128;
    private static final int MIN_PAYLOAD_LENGTH = IV_LENGTH + AAD_LENGTH + TAG_BITS / 8;

    private AesGcm() {}

    /**
     * Decrypt a WebSocket event payload.
     *
     * @param cipherKey Base64-encoded AES key (received during WebSocket cipher key exchange, cmd=1)
     * @param payload   raw bytes: [IV 16B][AAD 16B][ciphertext + GCM auth tag]
     * @return decrypted UTF-8 string (typically JSON)
     * @throws ZavaCryptoException if the key is invalid, payload too short, or decryption fails
     */
    public static String decrypt(String cipherKey, byte[] payload) {
        if (payload.length < MIN_PAYLOAD_LENGTH) {
            throw new ZavaCryptoException(
                    "AES-GCM payload too short: expected at least " + MIN_PAYLOAD_LENGTH
                            + " bytes, got " + payload.length);
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(cipherKey);

            // Extract IV (first 16 bytes)
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);

            // Extract AAD (next 16 bytes)
            byte[] aad = new byte[AAD_LENGTH];
            System.arraycopy(payload, IV_LENGTH, aad, 0, AAD_LENGTH);

            // Extract ciphertext + auth tag (remaining bytes)
            int dataOffset = IV_LENGTH + AAD_LENGTH;
            int dataLength = payload.length - dataOffset;
            byte[] ciphertext = new byte[dataLength];
            System.arraycopy(payload, dataOffset, ciphertext, 0, dataLength);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_BITS, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            cipher.updateAAD(aad);

            byte[] decrypted = cipher.doFinal(ciphertext);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (ZavaCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaCryptoException("AES-GCM decrypt failed", e);
        }
    }
}
