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

    /**
     * Change account avatar.
     *
     * @param imageData raw image bytes (JPEG/PNG)
     * @param width     original image width
     * @param height    original image height
     */
    public JsonNode changeAvatar(byte[] imageData, int width, int height) {
        java.util.Objects.requireNonNull(imageData, "imageData must not be null");

        try {
            Map<String, Object> metaOrigin = new LinkedHashMap<>();
            metaOrigin.put("w", width);
            metaOrigin.put("h", height);

            Map<String, Object> metaProcessed = new LinkedHashMap<>();
            metaProcessed.put("w", width);
            metaProcessed.put("h", height);
            metaProcessed.put("size", imageData.length);

            Map<String, Object> metaData = new LinkedHashMap<>();
            metaData.put("origin", metaOrigin);
            metaData.put("processed", metaProcessed);

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("avatarSize", 120);
            params.put("clientId", System.currentTimeMillis());
            params.put("language", context.getLanguage());
            params.put("metaData", MAPPER.writeValueAsString(metaData));

            String encrypted = dev.suprim.zava.internal.crypto.AesCbc.encodeAES(
                    context.getSecretKey(), MAPPER.writeValueAsString(params));

            String url = context.getServiceUrl("file") + "/api/profile/upavatar"
                    + "?zpw_ver=" + context.getOptions().getApiVersion()
                    + "&zpw_type=" + context.getOptions().getApiType();

            okhttp3.MultipartBody body = new okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("params", encrypted)
                    .addFormDataPart("fileContent", "avatar.jpg",
                            okhttp3.RequestBody.create(imageData,
                                    okhttp3.MediaType.parse("image/jpeg")))
                    .build();

            okhttp3.Response response = http.postMultipart(url, body);
            return responseHandler.handleRaw(response, true);
        } catch (dev.suprim.zava.exception.ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to change account avatar", e);
        }
    }
}
