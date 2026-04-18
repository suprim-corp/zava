package dev.suprim.zava.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a send/forward/delete/undo operation.
 */
public class SendResult {

    @JsonProperty("msgId")
    private long msgId;

    public SendResult() {}

    public SendResult(long msgId) {
        this.msgId = msgId;
    }

    public long getMsgId() { return msgId; }

    @Override
    public String toString() {
        return "SendResult{msgId=" + msgId + "}";
    }
}
