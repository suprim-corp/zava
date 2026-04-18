package dev.suprim.zava.group;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A group event received via WebSocket cmd=601 (act_type="group").
 */
public class GroupEvent {

    private final GroupEventType type;
    private final String act;
    private final JsonNode data;
    private final String threadId;
    private final boolean isSelf;

    public GroupEvent(GroupEventType type, String act, JsonNode data, String threadId, boolean isSelf) {
        this.type = type;
        this.act = act;
        this.data = data;
        this.threadId = threadId;
        this.isSelf = isSelf;
    }

    public GroupEventType getType() { return type; }
    public String getAct() { return act; }
    public JsonNode getData() { return data; }
    public String getThreadId() { return threadId; }
    public boolean isSelf() { return isSelf; }

    @Override
    public String toString() {
        return "GroupEvent{type=" + type + ", act=" + act + ", threadId=" + threadId + "}";
    }
}
