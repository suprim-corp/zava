package dev.suprim.zava.group;

/**
 * Group event types dispatched via WebSocket cmd=601.
 */
public enum GroupEventType {

    JOIN_REQUEST("join_request"),
    JOIN("join"),
    LEAVE("leave"),
    REMOVE_MEMBER("remove_member"),
    BLOCK_MEMBER("block_member"),

    UPDATE_SETTING("update_setting"),
    UPDATE("update"),
    NEW_LINK("new_link"),

    ADD_ADMIN("add_admin"),
    REMOVE_ADMIN("remove_admin"),

    NEW_PIN_TOPIC("new_pin_topic"),
    UPDATE_PIN_TOPIC("update_pin_topic"),
    REORDER_PIN_TOPIC("reorder_pin_topic"),

    UPDATE_BOARD("update_board"),
    REMOVE_BOARD("remove_board"),

    UPDATE_TOPIC("update_topic"),
    UNPIN_TOPIC("unpin_topic"),
    REMOVE_TOPIC("remove_topic"),

    ACCEPT_REMIND("accept_remind"),
    REJECT_REMIND("reject_remind"),
    REMIND_TOPIC("remind_topic"),

    UPDATE_AVATAR("update_avatar"),

    UNKNOWN("unknown");

    private final String value;

    GroupEventType(String value) { this.value = value; }

    public String getValue() { return value; }

    /**
     * Map the raw {@code act} string from the WebSocket control event to a typed enum.
     *
     * <p>Matches the logic of zca-js {@code getGroupEventType(act)}.
     */
    public static GroupEventType fromAct(String act) {
        if (act == null) return UNKNOWN;
        switch (act) {
            case "join_request": return JOIN_REQUEST;
            case "join": return JOIN;
            case "leave": return LEAVE;
            case "remove_member": return REMOVE_MEMBER;
            case "block_member": return BLOCK_MEMBER;
            case "update_setting": return UPDATE_SETTING;
            case "update": return UPDATE;
            case "new_link": return NEW_LINK;
            case "add_admin": return ADD_ADMIN;
            case "remove_admin": return REMOVE_ADMIN;
            case "new_pin_topic": return NEW_PIN_TOPIC;
            case "update_pin_topic": return UPDATE_PIN_TOPIC;
            case "reorder_pin_topic": return REORDER_PIN_TOPIC;
            case "update_board": return UPDATE_BOARD;
            case "remove_board": return REMOVE_BOARD;
            case "update_topic": return UPDATE_TOPIC;
            case "unpin_topic": return UNPIN_TOPIC;
            case "remove_topic": return REMOVE_TOPIC;
            case "accept_remind": return ACCEPT_REMIND;
            case "reject_remind": return REJECT_REMIND;
            case "remind_topic": return REMIND_TOPIC;
            case "update_avatar": return UPDATE_AVATAR;
            default: return UNKNOWN;
        }
    }
}
