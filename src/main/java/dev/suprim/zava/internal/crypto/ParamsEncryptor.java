package dev.suprim.zava.internal.crypto;

import dev.suprim.zava.exception.ZavaCryptoException;
import dev.suprim.zava.internal.session.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Login-specific parameter encryptor.
 *
 * <p>Creates a {@code zcid} (encrypted client identifier), derives an encryption key
 * from it, and encrypts login request data.
 *
 * <p>Equivalent to zca-js {@code class ParamsEncryptor}.
 *
 * <h3>Key derivation algorithm:</h3>
 * <ol>
 *     <li>{@code zcid = AES-CBC("type,imei,firstLaunchTime", HARDCODED_KEY, hex, uppercase)}</li>
 *     <li>{@code zcid_ext = random hex string (6-12 chars)}</li>
 *     <li>{@code md5Upper = MD5(zcid_ext).toUpperCase()}</li>
 *     <li>Split {@code md5Upper} into even/odd index chars → {@code md5Even}</li>
 *     <li>Split {@code zcid} into even/odd index chars → {@code zcidEven, zcidOdd}</li>
 *     <li>{@code encryptKey = md5Even[0..8] + zcidEven[0..12] + reverse(zcidOdd)[0..12]}</li>
 * </ol>
 */
public class ParamsEncryptor {

    private final String zcid;
    private final String zcidExt;
    private final String encryptKey;

    /**
     * Create a new ParamsEncryptor.
     *
     * @param type            API type (typically 30)
     * @param imei            device IMEI / identifier
     * @param firstLaunchTime first launch timestamp in milliseconds
     */
    public ParamsEncryptor(int type, String imei, long firstLaunchTime) {
        this.zcid = createZcid(type, imei, firstLaunchTime);
        this.zcidExt = randomHexString(6, 12);
        this.encryptKey = createEncryptKey(this.zcidExt, this.zcid);
    }

    /**
     * Get the derived encryption key.
     *
     * @return the 32-char encryption key
     */
    public String getEncryptKey() {
        return encryptKey;
    }

    /**
     * Get the parameters to include in the login request.
     *
     * @return map with {@code zcid}, {@code zcid_ext}, {@code enc_ver}
     */
    public Map<String, String> getParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("zcid", zcid);
        params.put("zcid_ext", zcidExt);
        params.put("enc_ver", "v2");
        return params;
    }

    /**
     * Encrypt data using the derived encryption key.
     *
     * <p>Uses AES-CBC with UTF-8 key, Base64 output, lowercase.
     *
     * @param data plaintext to encrypt
     * @return Base64-encoded ciphertext
     */
    public String encrypt(String data) {
        return AesCbc.encodeLogin(encryptKey, data, false, false);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private static String createZcid(int type, String imei, long firstLaunchTime) {
        String msg = type + "," + imei + "," + firstLaunchTime;
        return AesCbc.encodeLogin(Constants.LOGIN_ENCRYPT_KEY, msg, true, true);
    }

    static String createEncryptKey(String zcidExt, String zcid) {
        String md5Upper = Hashing.md5(zcidExt).toUpperCase();

        List<Character> md5Even = evenChars(md5Upper);
        List<Character> zcidEven = evenChars(zcid);
        List<Character> zcidOdd = oddChars(zcid);
        Collections.reverse(zcidOdd);

        if (md5Even.isEmpty() || zcidEven.isEmpty() || zcidOdd.isEmpty()) {
            throw new ZavaCryptoException("Failed to derive encrypt key: empty char lists");
        }

        StringBuilder sb = new StringBuilder(32);
        appendChars(sb, md5Even, 8);
        appendChars(sb, zcidEven, 12);
        appendChars(sb, zcidOdd, 12);

        return sb.toString();
    }

    /**
     * Extract characters at even indices (0, 2, 4, ...).
     * Equivalent to zca-js {@code processStr(e).even}.
     */
    private static List<Character> evenChars(String s) {
        List<Character> result = new ArrayList<>((s.length() + 1) / 2);
        for (int i = 0; i < s.length(); i += 2) {
            result.add(s.charAt(i));
        }
        return result;
    }

    /**
     * Extract characters at odd indices (1, 3, 5, ...).
     * Equivalent to zca-js {@code processStr(e).odd}.
     */
    private static List<Character> oddChars(String s) {
        List<Character> result = new ArrayList<>(s.length() / 2);
        for (int i = 1; i < s.length(); i += 2) {
            result.add(s.charAt(i));
        }
        return result;
    }

    private static void appendChars(StringBuilder sb, List<Character> chars, int count) {
        int limit = Math.min(count, chars.size());
        for (int i = 0; i < limit; i++) {
            sb.append(chars.get(i));
        }
    }

    /**
     * Generate a random hex string with length between min and max (inclusive).
     * Equivalent to zca-js {@code ParamsEncryptor.randomString(6, 12)}.
     */
    static String randomHexString(int min, int max) {
        int length = ThreadLocalRandom.current().nextInt(min, max + 1);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(ThreadLocalRandom.current().nextInt(16)));
        }
        return sb.toString();
    }
}
