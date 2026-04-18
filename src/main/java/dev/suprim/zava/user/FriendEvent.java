package dev.suprim.zava.user;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A friend event received via WebSocket cmd=601 (act_type="fr").
 */
public class FriendEvent {

    private final FriendEventType type;
    private final JsonNode data;
    private final String threadId;
    private final boolean isSelf;

    public FriendEvent(FriendEventType type, JsonNode data, String threadId, boolean isSelf) {
        this.type = type;
        this.data = data;
        this.threadId = threadId;
        this.isSelf = isSelf;
    }

    public FriendEventType getType() { return type; }
    public JsonNode getData() { return data; }
    public String getThreadId() { return threadId; }
    public boolean isSelf() { return isSelf; }

    @Override
    public String toString() {
        return "FriendEvent{type=" + type + ", threadId=" + threadId + "}";
    }
}
