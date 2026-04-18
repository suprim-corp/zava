package dev.suprim.zava.profile;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Profile operations: account info, profile updates.
 */
public class ProfileService extends BaseService {

    public ProfileService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /** Fetch the logged-in user's account info. */
    public JsonNode fetchAccountInfo() {
        return simpleGetRaw("profile", "/api/social/profile/me-v2");
    }

    /**
     * Update profile bio.
     *
     * @param bio the new bio text
     */
    public JsonNode updateBio(String bio) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sdesc", bio != null ? bio : "");
        params.put("imei", context.getImei());

        return encryptedPostRaw("profile", "/api/social/profile/update", params);
    }
}
