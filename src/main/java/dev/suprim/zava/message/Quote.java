package dev.suprim.zava.message;

/**
 * A quoted message reference.
 *
 * <p>Used when replying to a specific message in a thread.
 */
public class Quote {

    private final String ownerId;
    private final String globalMsgId;
    private final String cliMsgId;
    private final int cliMsgType;
    private final long ts;
    private final String msg;
    private final String attach;
    private final int ttl;

    private Quote(Builder builder) {
        this.ownerId = builder.ownerId;
        this.globalMsgId = builder.globalMsgId;
        this.cliMsgId = builder.cliMsgId;
        this.cliMsgType = builder.cliMsgType;
        this.ts = builder.ts;
        this.msg = builder.msg;
        this.attach = builder.attach;
        this.ttl = builder.ttl;
    }

    public String getOwnerId() { return ownerId; }
    public String getGlobalMsgId() { return globalMsgId; }
    public String getCliMsgId() { return cliMsgId; }
    public int getCliMsgType() { return cliMsgType; }
    public long getTs() { return ts; }
    public String getMsg() { return msg; }
    public String getAttach() { return attach; }
    public int getTtl() { return ttl; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String ownerId;
        private String globalMsgId;
        private String cliMsgId;
        private int cliMsgType = 1;
        private long ts;
        private String msg = "";
        private String attach = "";
        private int ttl;

        private Builder() {}

        public Builder ownerId(String ownerId) { this.ownerId = ownerId; return this; }
        public Builder globalMsgId(String globalMsgId) { this.globalMsgId = globalMsgId; return this; }
        public Builder cliMsgId(String cliMsgId) { this.cliMsgId = cliMsgId; return this; }
        public Builder cliMsgType(int cliMsgType) { this.cliMsgType = cliMsgType; return this; }
        public Builder ts(long ts) { this.ts = ts; return this; }
        public Builder msg(String msg) { this.msg = msg; return this; }
        public Builder attach(String attach) { this.attach = attach; return this; }
        public Builder ttl(int ttl) { this.ttl = ttl; return this; }

        public Quote build() { return new Quote(this); }
    }
}
