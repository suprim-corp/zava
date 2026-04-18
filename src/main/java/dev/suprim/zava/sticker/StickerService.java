package dev.suprim.zava.sticker;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sticker operations: search stickers.
 */
public class StickerService extends BaseService {

    public StickerService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /**
     * Search for stickers.
     *
     * @param keyword the search keyword
     * @param limit   max results (default 50)
     */
    public JsonNode search(String keyword, int limit) {
        Objects.requireNonNull(keyword, "keyword must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", keyword);
        params.put("limit", limit);
        params.put("srcType", 0);
        params.put("imei", context.getImei());

        return encryptedGetRaw("sticker", "/api/message/sticker/search", params);
    }

    /**
     * Search for stickers with default limit of 50.
     */
    public JsonNode search(String keyword) {
        return search(keyword, 50);
    }

    /**
     * Get detailed info for a sticker.
     *
     * @param stickerId the sticker ID
     */
    public JsonNode getDetail(int stickerId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sid", stickerId);

        return encryptedGetRaw("sticker", "/api/message/sticker/sticker_detail", params);
    }
}
