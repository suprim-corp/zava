package dev.suprim.zava.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * An undo (recalled) message event received via WebSocket.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UndoEvent {

    @JsonProperty("msgId") private String msgId;
    @JsonProperty("cliMsgId") private String cliMsgId;
    @JsonProperty("uidFrom") private String uidFrom;
    @JsonProperty("idTo") private String idTo;
    @JsonProperty("ts") private String ts;
    @JsonProperty("content") private JsonNode content;

    private String threadId;
    private boolean isSelf;
    private boolean isGroup;

    public UndoEvent() {}

    public void initialize(String uid, boolean isGroup) {
        this.isGroup = isGroup;
        this.isSelf = "0".equals(uidFrom);
        this.threadId = (isGroup || isSelf) ? idTo : uidFrom;
        if ("0".equals(idTo)) this.idTo = uid;
        if ("0".equals(uidFrom)) this.uidFrom = uid;
    }

    public String getMsgId() { return msgId; }
    public String getCliMsgId() { return cliMsgId; }
    public String getUidFrom() { return uidFrom; }
    public String getIdTo() { return idTo; }
    public String getTs() { return ts; }
    public JsonNode getContent() { return content; }
    public String getThreadId() { return threadId; }
    public boolean isSelf() { return isSelf; }
    public boolean isGroup() { return isGroup; }

    /** Get the global message ID that was undone. */
    public long getGlobalMsgId() {
        return content != null ? content.path("globalMsgId").asLong(0) : 0;
    }

    @Override
    public String toString() {
        return "UndoEvent{msgId=" + msgId + ", from=" + uidFrom
                + ", threadId=" + threadId + ", globalMsgId=" + getGlobalMsgId() + "}";
    }
}
