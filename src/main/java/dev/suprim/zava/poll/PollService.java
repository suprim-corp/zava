package dev.suprim.zava.poll;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.*;

/**
 * Poll operations: create polls, get details.
 */
public class PollService extends BaseService {

    public PollService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /**
     * Create a poll in a group.
     *
     * @param groupId           the group ID
     * @param question          the poll question
     * @param options           list of poll options
     * @param allowMultiChoice  allow selecting multiple options
     * @param allowAddOption    allow members to add new options
     * @param isAnonymous       anonymous voting
     * @param expiredTime       expiration time (0 = no expiry)
     */
    public JsonNode createPoll(String groupId, String question, List<String> options,
                               boolean allowMultiChoice, boolean allowAddOption,
                               boolean isAnonymous, long expiredTime) {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(options, "options must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("group_id", groupId);
        params.put("question", question);
        params.put("options", options);
        params.put("expired_time", expiredTime);
        params.put("pinAct", false);
        params.put("allow_multi_choices", allowMultiChoice);
        params.put("allow_add_new_option", allowAddOption);
        params.put("is_hide_vote_preview", false);
        params.put("is_anonymous", isAnonymous);
        params.put("poll_type", 0);
        params.put("src", 1);
        params.put("imei", context.getImei());

        return encryptedPostRaw("group", "/api/poll/create", params);
    }

    /**
     * Create a simple poll (multi-choice disabled, not anonymous).
     */
    public JsonNode createPoll(String groupId, String question, List<String> options) {
        return createPoll(groupId, question, options, false, false, false, 0);
    }

    /**
     * Get poll details.
     *
     * @param pollId the poll ID
     */
    public JsonNode getPollDetail(long pollId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("poll_id", pollId);
        params.put("imei", context.getImei());

        return encryptedPostRaw("group", "/api/poll/detail", params);
    }

    /**
     * Add new options to an existing poll.
     *
     * @param pollId    the poll ID
     * @param options   new options to add
     */
    public JsonNode addOptions(long pollId, List<String> options) {
        Objects.requireNonNull(options, "options must not be null");

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("poll_id", pollId);
            params.put("new_options", MAPPER.writeValueAsString(options));
            params.put("voted_option_ids", "[]");

            return encryptedGetRaw("group", "/api/poll/option/add", params);
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to add poll options", e);
        }
    }

    /**
     * Lock (end) a poll.
     *
     * @param pollId the poll ID
     */
    public JsonNode lock(long pollId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("poll_id", pollId);
        params.put("imei", context.getImei());

        return encryptedPostRaw("group", "/api/poll/end", params);
    }
}
