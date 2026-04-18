package dev.suprim.zava.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.conversation.ThreadType;

/**
 * A user (direct) message received via WebSocket.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserMessage {

    private final ThreadType type = ThreadType.USER;

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

    private String threadId;
    private boolean isSelf;

    public UserMessage() {}

    /**
     * Initialize computed fields from raw data and the logged-in UID.
     */
    public void initialize(String uid) {
        this.isSelf = "0".equals(uidFrom);
        this.threadId = isSelf ? idTo : uidFrom;
        if ("0".equals(idTo)) this.idTo = uid;
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
    public String getThreadId() { return threadId; }
    public boolean isSelf() { return isSelf; }

    /**
     * Get the text content of the message (if content is a string).
     */
    public String getTextContent() {
        if (content == null) return null;
        if (content.isTextual()) return content.asText();
        return content.toString();
    }

    @Override
    public String toString() {
        return "UserMessage{msgId=" + msgId + ", from=" + uidFrom
                + ", threadId=" + threadId + ", content=" + getTextContent() + "}";
    }
}
