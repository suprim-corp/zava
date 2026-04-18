package dev.suprim.zava.profile;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

/**
 * Profile operations: fetch account info.
 */
public class ProfileService extends BaseService {

    public ProfileService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /**
     * Fetch the logged-in user's account info.
     */
    public JsonNode fetchAccountInfo() {
        return simpleGetRaw("profile", "/api/social/profile/me-v2");
    }
}
