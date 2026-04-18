package dev.suprim.zava.internal.session;

import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.internal.http.ZavaCookieJar;

import java.util.List;

/**
 * Session state shared across all SDK components.
 *
 * <p>Built during the login flow and then effectively immutable
 * (the only mutation is cookie jar updates from Set-Cookie headers).
 *
 * <p>Consumer code never accesses this directly — it's internal.
 */
public class Context {

    private final String uid;
    private final String imei;
    private final String secretKey;
    private final String userAgent;
    private final String language;
    private final ZavaCookieJar cookieJar;
    private final ServiceMap serviceMap;
    private final Settings settings;
    private final ZavaOptions options;
    private final CallbackMap<UploadCallback> uploadCallbacks;
    private final List<String> wsUrls;

    private Context(Builder builder) {
        this.uid = builder.uid;
        this.imei = builder.imei;
        this.secretKey = builder.secretKey;
        this.userAgent = builder.userAgent;
        this.language = builder.language;
        this.cookieJar = builder.cookieJar;
        this.serviceMap = builder.serviceMap;
        this.settings = builder.settings;
        this.options = builder.options;
        this.uploadCallbacks = builder.uploadCallbacks != null
                ? builder.uploadCallbacks : new CallbackMap<>();
        this.wsUrls = builder.wsUrls;
    }

    public String getUid() { return uid; }
    public String getImei() { return imei; }
    public String getSecretKey() { return secretKey; }
    public String getUserAgent() { return userAgent; }
    public String getLanguage() { return language; }
    public ZavaCookieJar getCookieJar() { return cookieJar; }
    public ServiceMap getServiceMap() { return serviceMap; }
    public Settings getSettings() { return settings; }
    public ZavaOptions getOptions() { return options; }
    public CallbackMap<UploadCallback> getUploadCallbacks() { return uploadCallbacks; }
    public List<String> getWsUrls() { return wsUrls; }

    /**
     * Convenience: get primary URL for a service from the service map.
     */
    public String getServiceUrl(String serviceName) {
        return serviceMap.getUrl(serviceName);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String uid;
        private String imei;
        private String secretKey;
        private String userAgent;
        private String language = "vi";
        private ZavaCookieJar cookieJar;
        private ServiceMap serviceMap;
        private Settings settings;
        private ZavaOptions options;
        private CallbackMap<UploadCallback> uploadCallbacks;
        private List<String> wsUrls;

        private Builder() {}

        public Builder uid(String uid) { this.uid = uid; return this; }
        public Builder imei(String imei) { this.imei = imei; return this; }
        public Builder secretKey(String secretKey) { this.secretKey = secretKey; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder language(String language) { this.language = language; return this; }
        public Builder cookieJar(ZavaCookieJar cookieJar) { this.cookieJar = cookieJar; return this; }
        public Builder serviceMap(ServiceMap serviceMap) { this.serviceMap = serviceMap; return this; }
        public Builder settings(Settings settings) { this.settings = settings; return this; }
        public Builder options(ZavaOptions options) { this.options = options; return this; }
        public Builder uploadCallbacks(CallbackMap<UploadCallback> cb) { this.uploadCallbacks = cb; return this; }
        public Builder wsUrls(List<String> wsUrls) { this.wsUrls = wsUrls; return this; }

        public Context build() {
            return new Context(this);
        }
    }

    /**
     * Callback for upload completion events from WebSocket.
     */
    @FunctionalInterface
    public interface UploadCallback {
        void onComplete(String fileUrl, String fileId);
    }
}
