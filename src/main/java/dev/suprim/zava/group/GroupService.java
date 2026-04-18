package dev.suprim.zava.group;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.*;

/**
 * Group operations: info, create, members, settings.
 */
public class GroupService extends BaseService {

    public GroupService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /**
     * Get group info for one or more groups.
     *
     * @param groupIds one or more group IDs
     */
    public JsonNode getGroupInfo(String... groupIds) {
        Objects.requireNonNull(groupIds, "groupIds must not be null");

        Map<String, Integer> gridVerMap = new LinkedHashMap<>();
        for (String id : groupIds) {
            gridVerMap.put(id, 0);
        }

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("gridVerMap", MAPPER.writeValueAsString(gridVerMap));
            return encryptedPostRaw("group", "/api/group/getmg-v2", params);
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to get group info", e);
        }
    }

    /**
     * Get all groups the user belongs to.
     */
    public JsonNode getAllGroups() {
        return simpleGetRaw("group_poll", "/api/group/getlg/v4");
    }

    /**
     * Create a new group.
     *
     * @param name    group name (null for auto-generated)
     * @param members list of member user IDs
     */
    public JsonNode createGroup(String name, List<String> members) {
        Objects.requireNonNull(members, "members must not be null");
        if (members.isEmpty()) {
            throw new dev.suprim.zava.exception.ZavaException("Group must have at least one member");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("clientId", System.currentTimeMillis());
        params.put("gname", name != null ? name : String.valueOf(System.currentTimeMillis()));
        params.put("gdesc", null);
        params.put("members", members);

        List<Integer> memberTypes = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) memberTypes.add(-1);
        params.put("membersTypes", memberTypes);

        params.put("nameChanged", name != null ? 1 : 0);
        params.put("createLink", 1);
        params.put("clientLang", context.getLanguage());
        params.put("imei", context.getImei());
        params.put("zsource", 601);

        return encryptedPostRaw("group", "/api/group/create/v2", params);
    }

    /**
     * Add users to a group.
     *
     * @param groupId   the group ID
     * @param memberIds one or more user IDs to add
     */
    public JsonNode addUser(String groupId, String... memberIds) {
        Objects.requireNonNull(groupId, "groupId must not be null");
        List<String> members = Arrays.asList(memberIds);

        List<Integer> memberTypes = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) memberTypes.add(-1);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grid", groupId);
        params.put("members", members);
        params.put("memberTypes", memberTypes);
        params.put("imei", context.getImei());
        params.put("clientLang", context.getLanguage());

        return encryptedPostRaw("group", "/api/group/invite/v2", params);
    }

    /**
     * Remove users from a group.
     *
     * @param groupId   the group ID
     * @param memberIds one or more user IDs to remove
     */
    public JsonNode removeUser(String groupId, String... memberIds) {
        Objects.requireNonNull(groupId, "groupId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grid", groupId);
        params.put("members", Arrays.asList(memberIds));
        params.put("imei", context.getImei());

        return encryptedPostRaw("group", "/api/group/kickout", params);
    }

    /**
     * Transfer group ownership.
     *
     * @param groupId      the group ID
     * @param newOwnerId   the new owner's user ID
     */
    public JsonNode changeOwner(String groupId, String newOwnerId) {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(newOwnerId, "newOwnerId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grid", groupId);
        params.put("newAdminId", newOwnerId);
        params.put("imei", context.getImei());
        params.put("language", context.getLanguage());

        return encryptedGetRaw("group", "/api/group/change-owner", params);
    }

    /**
     * Change a group's name.
     *
     * @param groupId the group ID
     * @param name    the new group name
     */
    public JsonNode changeName(String groupId, String name) {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(name, "name must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grid", groupId);
        params.put("gname", name.isEmpty() ? String.valueOf(System.currentTimeMillis()) : name);
        params.put("imei", context.getImei());

        return encryptedPostRaw("group", "/api/group/updateinfo", params);
    }

    /**
     * Get members info for a group.
     */
    public JsonNode getMembersInfo(String... memberIds) {
        Objects.requireNonNull(memberIds, "memberIds must not be null");

        Map<String, Integer> memberMap = new LinkedHashMap<>();
        for (String id : memberIds) memberMap.put(id, 0);

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("friend_pversion_map", MAPPER.writeValueAsString(memberMap));
            return encryptedGetRaw("profile", "/api/social/group/members", params);
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to get members info", e);
        }
    }

    /**
     * Add a deputy (admin) to a group.
     */
    public JsonNode addDeputy(String groupId, String... memberIds) {
        Objects.requireNonNull(groupId, "groupId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grid", groupId);
        params.put("members", Arrays.asList(memberIds));
        params.put("imei", context.getImei());

        return encryptedGetRaw("group", "/api/group/admins/add", params);
    }

    /**
     * Remove a deputy (admin) from a group.
     */
    public JsonNode removeDeputy(String groupId, String... memberIds) {
        Objects.requireNonNull(groupId, "groupId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grid", groupId);
        params.put("members", Arrays.asList(memberIds));
        params.put("imei", context.getImei());

        return encryptedGetRaw("group", "/api/group/admins/remove", params);
    }

    /**
     * Update group settings.
     */
    public JsonNode updateSettings(String groupId, Map<String, Object> settings) {
        Objects.requireNonNull(groupId, "groupId must not be null");

        Map<String, Object> params = new LinkedHashMap<>(settings);
        params.put("grid", groupId);
        params.put("imei", context.getImei());

        return encryptedGetRaw("group", "/api/group/setting/update", params);
    }

    /**
     * Leave a group.
     */
    public JsonNode leave(String groupId) {
        Objects.requireNonNull(groupId, "groupId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grids", List.of(groupId));
        params.put("imei", context.getImei());
        params.put("silent", 0);
        params.put("language", context.getLanguage());

        return encryptedPostRaw("group", "/api/group/leave", params);
    }

    /**
     * Disperse (dissolve) a group.
     */
    public JsonNode disperse(String groupId) {
        Objects.requireNonNull(groupId, "groupId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grid", groupId);
        params.put("imei", context.getImei());

        return encryptedPostRaw("group", "/api/group/disperse", params);
    }

    /**
     * Get group chat history.
     *
     * @param groupId the group ID
     * @param count   number of messages to fetch
     */
    public JsonNode getChatHistory(String groupId, int count) {
        Objects.requireNonNull(groupId, "groupId must not be null");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grid", groupId);
        params.put("count", count);

        return encryptedGetRaw("group", "/api/group/history", params);
    }

    /**
     * Get group chat history (default 50 messages).
     */
    public JsonNode getChatHistory(String groupId) {
        return getChatHistory(groupId, 50);
    }
}
