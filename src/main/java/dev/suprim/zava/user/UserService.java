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
}
