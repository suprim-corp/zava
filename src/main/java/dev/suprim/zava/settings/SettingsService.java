package dev.suprim.zava.settings;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.conversation.ThreadType;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.*;

/**
 * Settings and conversation management operations.
 */
public class SettingsService extends BaseService {

    public SettingsService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /** Get mute settings for chats and groups. */
    public JsonNode getMute() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imei", context.getImei());
        return encryptedGetRaw("profile", "/api/social/profile/getmute", params);
    }

    /**
     * Set mute for a conversation.
     *
     * @param threadId the thread ID
     * @param type     USER or GROUP
     * @param duration mute duration in seconds (0 = forever)
     * @param mute     true to mute, false to unmute
     */
    public JsonNode setMute(String threadId, ThreadType type, int duration, boolean mute) {
        Objects.requireNonNull(threadId, "threadId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("toid", threadId);
        params.put("duration", duration);
        params.put("action", mute ? 1 : 3);
        params.put("startTime", System.currentTimeMillis());
        params.put("muteType", type == ThreadType.USER ? 1 : 2);
        params.put("imei", context.getImei());

        return encryptedPostRaw("profile", "/api/social/profile/setmute", params);
    }

    /** Get conversation labels. */
    public JsonNode getLabels() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imei", context.getImei());
        return encryptedGetRaw("label", "/api/convlabel/get", params);
    }

    /**
     * Delete a conversation.
     *
     * @param threadId the thread ID
     * @param type     USER or GROUP
     * @param onlyMe   if true, delete only for yourself
     */
    public JsonNode deleteChat(String threadId, ThreadType type, boolean onlyMe) {
        Objects.requireNonNull(threadId, "threadId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        if (type == ThreadType.USER) {
            params.put("toid", threadId);
        } else {
            params.put("grid", threadId);
        }
        params.put("cliMsgId", System.currentTimeMillis());
        params.put("onlyMe", onlyMe ? 1 : 0);
        params.put("imei", context.getImei());

        String service = type == ThreadType.USER ? "chat" : "group";
        String path = type == ThreadType.USER
                ? "/api/message/deleteconver"
                : "/api/group/deleteconver";

        return encryptedPostRaw(service, path, params);
    }

    /**
     * Get pinned conversations.
     */
    public JsonNode getPinConversations() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imei", context.getImei());
        return encryptedGetRaw("conversation", "/api/pinconvers/list", params);
    }

    /**
     * Pin or unpin a conversation.
     *
     * @param threadId the thread ID
     * @param pin      true to pin, false to unpin
     */
    public JsonNode setPinConversation(String threadId, boolean pin) {
        Objects.requireNonNull(threadId, "threadId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("actionType", pin ? 1 : 2);
        params.put("conversations", List.of(threadId));

        return encryptedPostRaw("conversation", "/api/pinconvers/updatev2", params);
    }
}
