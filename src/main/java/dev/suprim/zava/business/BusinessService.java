package dev.suprim.zava.business;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.*;

/**
 * Business operations: auto-reply, quick messages.
 */
public class BusinessService extends BaseService {

    public BusinessService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    // ── Auto-reply ───────────────────────────────────────────────────────

    /** Get auto-reply list. */
    public JsonNode getAutoReplyList() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("version", 0);
        params.put("cliLang", context.getLanguage());

        return encryptedGetRaw("auto_reply", "/api/autoreply/list", params);
    }

    /**
     * Create an auto-reply.
     *
     * @param content    the reply message content
     * @param enabled    whether the auto-reply is active
     * @param startTime  start time in milliseconds
     * @param endTime    end time in milliseconds
     */
    public JsonNode createAutoReply(String content, boolean enabled, long startTime, long endTime) {
        Objects.requireNonNull(content, "content must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("cliLang", context.getLanguage());
        params.put("enable", enabled);
        params.put("content", content);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        params.put("recurrence", 0);
        params.put("scope", 0);
        params.put("uids", List.of());

        return encryptedPostRaw("auto_reply", "/api/autoreply/create", params);
    }

    // ── Quick messages ───────────────────────────────────────────────────

    /** Get quick message list. */
    public JsonNode getQuickMessageList() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("version", 0);
        params.put("lang", context.getLanguage());
        params.put("imei", context.getImei());

        return encryptedGetRaw("quick_message", "/api/quickmessage/list", params);
    }

    /**
     * Add a quick message.
     *
     * @param keyword the trigger keyword
     * @param title   the quick message content
     */
    public JsonNode addQuickMessage(String keyword, String title) {
        Objects.requireNonNull(keyword, "keyword must not be null");
        Objects.requireNonNull(title, "title must not be null");

        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("title", title);
            message.put("params", "{}");

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("keyword", keyword);
            params.put("message", MAPPER.writeValueAsString(message));
            params.put("type", 0); // text type
            params.put("imei", context.getImei());

            return encryptedGetRaw("quick_message", "/api/quickmessage/create", params);
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to add quick message", e);
        }
    }
}
