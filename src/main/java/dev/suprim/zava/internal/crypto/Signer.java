package dev.suprim.zava.internal.crypto;

import dev.suprim.zava.internal.session.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Request signing for Zalo API calls.
 *
 * <p>Equivalent to zca-js {@code getSignKey(type, params)}.
 *
 * <p>Algorithm: sort param keys alphabetically, concatenate
 * {@code "zsecure" + type + value1 + value2 + ...}, then MD5 hash.
 */
public final class Signer {

    private Signer() {}

    /**
     * Compute the sign key for an API request.
     *
     * @param type   the request type (e.g. "getlogininfo", "getserverinfo")
     * @param params the request parameters (keys are sorted alphabetically)
     * @return 32-char lowercase hex MD5 sign key
     */
    public static String getSignKey(String type, Map<String, ?> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        sb.append(Constants.SIGN_KEY_PREFIX).append(type);
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null) {
                sb.append(value);
            }
        }

        return Hashing.md5(sb.toString());
    }
}
