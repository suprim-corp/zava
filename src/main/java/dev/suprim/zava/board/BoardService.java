package dev.suprim.zava.board;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.*;

/**
 * Board/notes operations: create notes, list boards.
 */
public class BoardService extends BaseService {

    public BoardService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /**
     * Create a note in a group.
     *
     * @param groupId   the group ID
     * @param title     the note title
     * @param color     the note color (e.g. "#ffffff")
     * @param emoji     the note emoji
     */
    public JsonNode createNote(String groupId, String title, String color, String emoji) {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(title, "title must not be null");

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("grid", groupId);
            params.put("type", 0);
            params.put("color", color != null ? color : "#ffffff");
            params.put("emoji", emoji != null ? emoji : "");
            params.put("startTime", 0);
            params.put("duration", 0);
            params.put("params", MAPPER.writeValueAsString(Map.of("title", title)));
            params.put("repeat", 0);
            params.put("src", 1);
            params.put("imei", context.getImei());
            params.put("pinAct", false);

            return encryptedPostRaw("group_board", "/api/board/topic/createv2", params);
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to create note", e);
        }
    }

    /**
     * Edit a note.
     */
    public JsonNode editNote(String groupId, String topicId, String title, String color, String emoji) {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(topicId, "topicId must not be null");

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("grid", groupId);
            params.put("type", 0);
            params.put("color", color != null ? color : "#ffffff");
            params.put("emoji", emoji != null ? emoji : "");
            params.put("startTime", 0);
            params.put("duration", 0);
            params.put("params", MAPPER.writeValueAsString(Map.of("title", title != null ? title : "")));
            params.put("topicId", topicId);
            params.put("repeat", 0);
            params.put("imei", context.getImei());
            params.put("pinAct", false);

            return encryptedPostRaw("group_board", "/api/board/topic/updatev2", params);
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to edit note", e);
        }
    }

    /**
     * Get board list for a group.
     *
     * @param groupId   the group ID
     * @param boardType 0 = notes, 1 = reminders
     * @param count     number of items
     * @param page      page number
     */
    public JsonNode getListBoard(String groupId, int boardType, int count, int page) {
        Objects.requireNonNull(groupId, "groupId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("group_id", groupId);
        params.put("board_type", boardType);
        params.put("page", page);
        params.put("count", count);
        params.put("last_id", 0);
        params.put("last_type", 0);
        params.put("imei", context.getImei());

        return encryptedGetRaw("group_board", "/api/board/list", params);
    }

    /** Get notes board for a group (default 50, page 1). */
    public JsonNode getListBoard(String groupId) {
        return getListBoard(groupId, 0, 50, 1);
    }
}
