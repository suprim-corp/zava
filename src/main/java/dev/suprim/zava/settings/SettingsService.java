package dev.suprim.zava.settings;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings operations: mute, labels.
 */
public class SettingsService extends BaseService {

    public SettingsService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /**
     * Get mute settings for chats and groups.
     */
    public JsonNode getMute() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imei", context.getImei());

        return encryptedGetRaw("profile", "/api/social/profile/getmute", params);
    }

    /**
     * Get conversation labels.
     */
    public JsonNode getLabels() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imei", context.getImei());

        return encryptedGetRaw("label", "/api/convlabel/get", params);
    }
}
