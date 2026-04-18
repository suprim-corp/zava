package dev.suprim.zava.user;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.*;

/**
 * User/friend operations: find, get info, friends list, block/unblock, alias.
 */
public class UserService extends BaseService {

    public UserService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /**
     * Find a user by phone number.
     */
    public JsonNode findUser(String phoneNumber) {
        return findUser(phoneNumber, 240);
    }

    /**
     * Find a user by phone number with avatar size.
     *
     * @param phoneNumber the phone number (e.g. "0912345678" or "84912345678")
     * @param avatarSize  avatar size in pixels (120 or 240)
     */
    public JsonNode findUser(String phoneNumber, int avatarSize) {
        Objects.requireNonNull(phoneNumber, "phoneNumber must not be null");

        if (phoneNumber.startsWith("0") && "vi".equals(context.getLanguage())) {
            phoneNumber = "84" + phoneNumber.substring(1);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("phone", phoneNumber);
        params.put("avatar_size", avatarSize);
        params.put("language", context.getLanguage());
        params.put("imei", context.getImei());
        params.put("reqSrc", 40);

        return encryptedGetRaw("friend", "/api/friend/profile/get", params);
    }

    /**
     * Get all friends.
     */
    public JsonNode getAllFriends() {
        return getAllFriends(20000, 1, 120);
    }

    /**
     * Get all friends with pagination.
     *
     * @param count      max number of friends
     * @param page       page number (1-based)
     * @param avatarSize avatar size in pixels
     */
    public JsonNode getAllFriends(int count, int page, int avatarSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("incInvalid", 1);
        params.put("page", page);
        params.put("count", count);
        params.put("avatar_size", avatarSize);
        params.put("actiontime", 0);
        params.put("imei", context.getImei());

        return encryptedGetRaw("profile", "/api/social/friend/getfriends", params);
    }

    /**
     * Block a user.
     */
    public JsonNode blockUser(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fid", userId);
        params.put("imei", context.getImei());

        return encryptedPostRaw("friend", "/api/friend/block", params);
    }

    /**
     * Unblock a user.
     */
    public JsonNode unblockUser(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fid", userId);
        params.put("imei", context.getImei());

        return encryptedPostRaw("friend", "/api/friend/unblock", params);
    }

    /**
     * Change a friend's alias (nickname).
     */
    public JsonNode changeFriendAlias(String friendId, String alias) {
        Objects.requireNonNull(friendId, "friendId must not be null");
        Objects.requireNonNull(alias, "alias must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("friendId", friendId);
        params.put("alias", alias);
        params.put("imei", context.getImei());

        return encryptedGetRaw("alias", "/api/alias/update", params);
    }

    /**
     * Get alias list.
     */
    public JsonNode getAliasList() {
        return getAliasList(100, 1);
    }

    /**
     * Get alias list with pagination.
     */
    public JsonNode getAliasList(int count, int page) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("count", count);
        params.put("imei", context.getImei());

        return encryptedGetRaw("alias", "/api/alias/list", params);
    }

    /**
     * Find a user by username.
     */
    public JsonNode findUserByUsername(String username) {
        return findUserByUsername(username, 240);
    }

    /**
     * Find a user by username with avatar size.
     */
    public JsonNode findUserByUsername(String username, int avatarSize) {
        Objects.requireNonNull(username, "username must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("user_name", username);
        params.put("avatar_size", avatarSize);

        return encryptedGetRaw("friend", "/api/friend/search/by-user-name", params);
    }

    /**
     * Get user info for one or more user IDs.
     */
    public JsonNode getUserInfo(String... userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null");

        Map<String, Integer> friendMap = new LinkedHashMap<>();
        for (String id : userIds) friendMap.put(id, 0);

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("phonebook_version", 0);
            params.put("friend_pversion_map", MAPPER.writeValueAsString(friendMap));
            params.put("avatar_size", 240);
            params.put("language", context.getLanguage());
            params.put("show_online_status", 1);
            params.put("imei", context.getImei());

            return encryptedPostRaw("profile", "/api/social/friend/getprofiles/v2", params);
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to get user info", e);
        }
    }

    /**
     * Send a friend request.
     */
    public JsonNode sendFriendRequest(String userId, String message) {
        Objects.requireNonNull(userId, "userId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("toid", userId);
        params.put("msg", message != null ? message : "");
        params.put("reqsrc", 30);
        params.put("imei", context.getImei());
        params.put("language", context.getLanguage());
        params.put("srcParams", "");

        return encryptedPostRaw("friend", "/api/friend/sendreq", params);
    }

    /**
     * Accept a friend request.
     */
    public JsonNode acceptFriendRequest(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fid", userId);
        params.put("language", context.getLanguage());

        return encryptedPostRaw("friend", "/api/friend/accept", params);
    }

    /**
     * Remove a friend.
     */
    public JsonNode removeFriend(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fid", userId);
        params.put("imei", context.getImei());

        return encryptedPostRaw("friend", "/api/friend/remove", params);
    }
}
