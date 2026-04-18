package dev.suprim.zava.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.*;

/**
 * Reminder operations: create, list reminders.
 */
public class ReminderService extends BaseService {

    public ReminderService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /**
     * Create a reminder in a group.
     *
     * @param groupId   the group ID
     * @param title     the reminder title
     * @param startTime start time in milliseconds
     * @param duration  duration in seconds
     * @param repeat    repeat interval (0 = no repeat)
     */
    public JsonNode createReminder(String groupId, String title,
                                   long startTime, int duration, int repeat) {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(title, "title must not be null");

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("grid", groupId);
            params.put("type", 2); // reminder type
            params.put("color", "#ffffff");
            params.put("emoji", "");
            params.put("startTime", startTime);
            params.put("duration", duration);
            params.put("params", MAPPER.writeValueAsString(Map.of("title", title)));
            params.put("repeat", repeat);
            params.put("src", 1);
            params.put("imei", context.getImei());
            params.put("pinAct", false);

            return encryptedPostRaw("group_board", "/api/board/topic/createv2", params);
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to create reminder", e);
        }
    }

    /**
     * Get reminders for a group.
     */
    public JsonNode getListReminder(String groupId) {
        Objects.requireNonNull(groupId, "groupId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("group_id", groupId);
        params.put("board_type", 1);
        params.put("page", 1);
        params.put("count", 50);
        params.put("last_id", 0);
        params.put("last_type", 0);
        params.put("imei", context.getImei());

        return encryptedGetRaw("group_board", "/api/board/listReminder", params);
    }
}
