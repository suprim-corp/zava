package dev.suprim.zava;

import java.net.Proxy;

/**
 * SDK configuration.
 */
public final class ZavaOptions {

    private final boolean selfListen;
    private final boolean logging;
    private final int apiType;
    private final int apiVersion;
    private final Proxy proxy;
    private final String loginBaseUrl;

    private ZavaOptions(Builder builder) {
        this.selfListen = builder.selfListen;
        this.logging = builder.logging;
        this.apiType = builder.apiType;
        this.apiVersion = builder.apiVersion;
        this.proxy = builder.proxy;
        this.loginBaseUrl = builder.loginBaseUrl;
    }

    public static ZavaOptions defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSelfListen() { return selfListen; }
    public boolean isLogging() { return logging; }
    public int getApiType() { return apiType; }
    public int getApiVersion() { return apiVersion; }
    public Proxy getProxy() { return proxy; }
    public String getLoginBaseUrl() { return loginBaseUrl; }

    public static final class Builder {
        private boolean selfListen = false;
        private boolean logging = true;
        private int apiType = 30;
        private int apiVersion = 671;
        private Proxy proxy = null;
        private String loginBaseUrl = "https://wpa.chat.zalo.me/api/login";

        private Builder() {}

        public Builder selfListen(boolean selfListen) { this.selfListen = selfListen; return this; }
        public Builder logging(boolean logging) { this.logging = logging; return this; }
        public Builder apiType(int apiType) { this.apiType = apiType; return this; }
        public Builder apiVersion(int apiVersion) { this.apiVersion = apiVersion; return this; }
        public Builder proxy(Proxy proxy) { this.proxy = proxy; return this; }
        /** Override login base URL (for testing). */
        public Builder loginBaseUrl(String loginBaseUrl) { this.loginBaseUrl = loginBaseUrl; return this; }

        public ZavaOptions build() {
            return new ZavaOptions(this);
        }
    }
}
