package dev.suprim.zava.reaction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A reaction event received via WebSocket.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReactionEvent {

    @JsonProperty("actionId") private String actionId;
    @JsonProperty("msgId") private String msgId;
    @JsonProperty("cliMsgId") private String cliMsgId;
    @JsonProperty("msgType") private String msgType;
    @JsonProperty("uidFrom") private String uidFrom;
    @JsonProperty("idTo") private String idTo;
    @JsonProperty("dName") private String displayName;
    @JsonProperty("content") private JsonNode content;
    @JsonProperty("ts") private String ts;
    @JsonProperty("ttl") private int ttl;

    private String threadId;
    private boolean isSelf;
    private boolean isGroup;

    public ReactionEvent() {}

    public void initialize(String uid, boolean isGroup) {
        this.isGroup = isGroup;
        this.isSelf = "0".equals(uidFrom);
        this.threadId = (isGroup || isSelf) ? idTo : uidFrom;
        if ("0".equals(idTo)) this.idTo = uid;
        if ("0".equals(uidFrom)) this.uidFrom = uid;
    }

    public String getActionId() { return actionId; }
    public String getMsgId() { return msgId; }
    public String getCliMsgId() { return cliMsgId; }
    public String getMsgType() { return msgType; }
    public String getUidFrom() { return uidFrom; }
    public String getIdTo() { return idTo; }
    public String getDisplayName() { return displayName; }
    public JsonNode getContent() { return content; }
    public String getTs() { return ts; }
    public int getTtl() { return ttl; }
    public String getThreadId() { return threadId; }
    public boolean isSelf() { return isSelf; }
    public boolean isGroup() { return isGroup; }

    @Override
    public String toString() {
        return "ReactionEvent{msgId=" + msgId + ", from=" + uidFrom
                + ", threadId=" + threadId + ", isGroup=" + isGroup + "}";
    }
}
