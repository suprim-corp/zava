package dev.suprim.zava.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A seen message receipt received via WebSocket.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeenEvent {

    // User seen fields
    @JsonProperty("idTo") private String idTo;
    @JsonProperty("msgId") private String msgId;
    @JsonProperty("realMsgId") private String realMsgId;

    // Group seen fields
    @JsonProperty("groupId") private String groupId;
    @JsonProperty("seenUids") private List<String> seenUids;

    private boolean isGroup;
    private boolean isSelf;

    public SeenEvent() {}

    public void initialize(String uid, boolean isGroup) {
        this.isGroup = isGroup;
        if (isGroup) {
            this.isSelf = seenUids != null && seenUids.contains(uid);
        } else {
            this.isSelf = false;
        }
    }

    public String getThreadId() {
        return isGroup ? groupId : idTo;
    }

    public String getMsgId() { return msgId; }
    public String getRealMsgId() { return realMsgId; }
    public String getGroupId() { return groupId; }
    public List<String> getSeenUids() { return seenUids; }
    public boolean isGroup() { return isGroup; }
    public boolean isSelf() { return isSelf; }

    @Override
    public String toString() {
        return "SeenEvent{threadId=" + getThreadId() + ", msgId=" + msgId + ", isGroup=" + isGroup + "}";
    }
}
