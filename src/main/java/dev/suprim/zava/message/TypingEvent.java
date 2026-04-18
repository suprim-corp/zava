package dev.suprim.zava.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.suprim.zava.conversation.ThreadType;

/**
 * A typing indicator event received via WebSocket.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypingEvent {

    @JsonProperty("uid") private String uid;
    @JsonProperty("ts") private String ts;
    @JsonProperty("isPC") private int isPC;
    @JsonProperty("gid") private String gid; // group only

    public TypingEvent() {}

    public String getUid() { return uid; }
    public String getTs() { return ts; }
    public boolean isPC() { return isPC == 1; }
    public String getGid() { return gid; }

    public boolean isGroup() { return gid != null && !gid.isEmpty(); }

    public ThreadType getType() {
        return isGroup() ? ThreadType.GROUP : ThreadType.USER;
    }

    public String getThreadId() {
        return isGroup() ? gid : uid;
    }

    @Override
    public String toString() {
        return "TypingEvent{uid=" + uid + ", threadId=" + getThreadId()
                + ", isGroup=" + isGroup() + "}";
    }
}
