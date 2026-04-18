package dev.suprim.zava.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A delivered message receipt received via WebSocket.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveredEvent {

    @JsonProperty("msgId") private String msgId;
    @JsonProperty("seen") private int seen;
    @JsonProperty("deliveredUids") private List<String> deliveredUids;
    @JsonProperty("seenUids") private List<String> seenUids;
    @JsonProperty("realMsgId") private String realMsgId;
    @JsonProperty("mSTs") private long mSTs;

    // Group only
    @JsonProperty("groupId") private String groupId;

    private boolean isGroup;
    private boolean isSelf;

    public DeliveredEvent() {}

    public void initialize(String uid, boolean isGroup) {
        this.isGroup = isGroup;
        if (isGroup) {
            this.isSelf = deliveredUids != null && deliveredUids.contains(uid);
        } else {
            this.isSelf = false;
        }
    }

    public String getThreadId() {
        if (isGroup) return groupId;
        return (deliveredUids != null && !deliveredUids.isEmpty()) ? deliveredUids.get(0) : null;
    }

    public String getMsgId() { return msgId; }
    public int getSeen() { return seen; }
    public List<String> getDeliveredUids() { return deliveredUids; }
    public List<String> getSeenUids() { return seenUids; }
    public String getRealMsgId() { return realMsgId; }
    public long getMSTs() { return mSTs; }
    public String getGroupId() { return groupId; }
    public boolean isGroup() { return isGroup; }
    public boolean isSelf() { return isSelf; }

    @Override
    public String toString() {
        return "DeliveredEvent{threadId=" + getThreadId() + ", msgId=" + msgId + ", isGroup=" + isGroup + "}";
    }
}
