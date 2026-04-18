package dev.suprim.zava.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.conversation.ThreadType;

import java.util.List;

/**
 * A group message received via WebSocket.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupMessage {

    private final ThreadType type = ThreadType.GROUP;

    @JsonProperty("actionId") private String actionId;
    @JsonProperty("msgId") private String msgId;
    @JsonProperty("cliMsgId") private String cliMsgId;
    @JsonProperty("msgType") private String msgType;
    @JsonProperty("uidFrom") private String uidFrom;
    @JsonProperty("idTo") private String idTo;
    @JsonProperty("dName") private String displayName;
    @JsonProperty("ts") private String ts;
    @JsonProperty("status") private int status;
    @JsonProperty("content") private JsonNode content;
    @JsonProperty("notify") private String notify;
    @JsonProperty("ttl") private int ttl;
    @JsonProperty("cmd") private int cmd;
    @JsonProperty("st") private int st;
    @JsonProperty("at") private int at;
    @JsonProperty("realMsgId") private String realMsgId;
    @JsonProperty("quote") private JsonNode quote;
    @JsonProperty("mentions") private List<Mention> mentions;

    private String threadId;
    private boolean isSelf;

    public GroupMessage() {}

    /**
     * Initialize computed fields from raw data and the logged-in UID.
     */
    public void initialize(String uid) {
        this.isSelf = "0".equals(uidFrom);
        this.threadId = idTo;
        if ("0".equals(uidFrom)) this.uidFrom = uid;
    }

    public ThreadType getType() { return type; }
    public String getActionId() { return actionId; }
    public String getMsgId() { return msgId; }
    public String getCliMsgId() { return cliMsgId; }
    public String getMsgType() { return msgType; }
    public String getUidFrom() { return uidFrom; }
    public String getIdTo() { return idTo; }
    public String getDisplayName() { return displayName; }
    public String getTs() { return ts; }
    public int getStatus() { return status; }
    public JsonNode getContent() { return content; }
    public String getNotify() { return notify; }
    public int getTtl() { return ttl; }
    public int getCmd() { return cmd; }
    public String getRealMsgId() { return realMsgId; }
    public JsonNode getQuote() { return quote; }
    public List<Mention> getMentions() { return mentions; }
    public String getThreadId() { return threadId; }
    public boolean isSelf() { return isSelf; }

    public String getTextContent() {
        if (content == null) return null;
        if (content.isTextual()) return content.asText();
        return content.toString();
    }

    @Override
    public String toString() {
        return "GroupMessage{msgId=" + msgId + ", from=" + uidFrom
                + ", threadId=" + threadId + ", content=" + getTextContent() + "}";
    }
}
