package dev.suprim.zava.user;

/**
 * Friend event types dispatched via WebSocket cmd=601.
 */
public enum FriendEventType {

    ADD,
    REMOVE,

    REQUEST,
    UNDO_REQUEST,
    REJECT_REQUEST,

    SEEN_FRIEND_REQUEST,

    BLOCK,
    UNBLOCK,
    BLOCK_CALL,
    UNBLOCK_CALL,

    PIN_UNPIN,
    PIN_CREATE,

    UNKNOWN;

    /**
     * Map the raw {@code act} string from the WebSocket control event to a typed enum.
     *
     * <p>Matches the logic of zca-js {@code getFriendEventType(act)}.
     */
    public static FriendEventType fromAct(String act) {
        if (act == null) return UNKNOWN;
        switch (act) {
            case "add": case "add_friend": return ADD;
            case "remove": case "remove_friend": return REMOVE;
            case "req_v2": return REQUEST;
            case "undo_req": return UNDO_REQUEST;
            case "reject_req": return REJECT_REQUEST;
            case "seen_fr_req": return SEEN_FRIEND_REQUEST;
            case "block": return BLOCK;
            case "unblock": return UNBLOCK;
            case "block_call": return BLOCK_CALL;
            case "unblock_call": return UNBLOCK_CALL;
            case "pin_unpin": return PIN_UNPIN;
            case "pin_create": return PIN_CREATE;
            default: return UNKNOWN;
        }
    }
}
