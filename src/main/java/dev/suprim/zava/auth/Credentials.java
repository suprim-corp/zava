package dev.suprim.zava.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Login credentials for the Zava SDK.
 *
 * <p>Contains the IMEI (device identifier), browser cookies, user agent,
 * and language. These are required for the cookie-based login flow.
 *
 * <pre>{@code
 * Credentials creds = Credentials.builder()
 *     .imei("device-id")
 *     .cookies(cookieList)
 *     .userAgent("Mozilla/5.0 ...")
 *     .build();
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Credentials {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonProperty("imei")
    private final String imei;

    @JsonProperty("cookies")
    private final List<CookieEntry> cookies;

    @JsonProperty("userAgent")
    private final String userAgent;

    @JsonProperty("language")
    private final String language;

    // For Jackson deserialization
    private Credentials() {
        this.imei = null;
        this.cookies = null;
        this.userAgent = null;
        this.language = "vi";
    }

    private Credentials(Builder builder) {
        this.imei = Objects.requireNonNull(builder.imei, "imei must not be null");
        this.cookies = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(builder.cookies, "cookies must not be null")));
        this.userAgent = Objects.requireNonNull(builder.userAgent, "userAgent must not be null");
        this.language = builder.language;
    }

    public String getImei() { return imei; }
    public List<CookieEntry> getCookies() { return cookies; }
    public String getUserAgent() { return userAgent; }
    public String getLanguage() { return language; }

    /**
     * Save credentials to a JSON file.
     */
    public void saveTo(Path path) throws IOException {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), this);
    }

    /**
     * Load credentials from a JSON file.
     */
    public static Credentials loadFrom(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), Credentials.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String imei;
        private List<CookieEntry> cookies;
        private String userAgent;
        private String language = "vi";

        private Builder() {}

        public Builder imei(String imei) { this.imei = imei; return this; }
        public Builder cookies(List<CookieEntry> cookies) { this.cookies = cookies; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder language(String language) { this.language = language; return this; }

        public Credentials build() {
            return new Credentials(this);
        }
    }

    /**
     * A single cookie entry matching the Zalo/browser cookie export format.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CookieEntry {

        @JsonProperty("name")
        private String name;

        @JsonProperty("value")
        private String value;

        @JsonProperty("domain")
        private String domain;

        @JsonProperty("path")
        private String path;

        @JsonProperty("secure")
        private boolean secure;

        @JsonProperty("httpOnly")
        private boolean httpOnly;

        @JsonProperty("expirationDate")
        private long expirationDate;

        public CookieEntry() {}

        public CookieEntry(String name, String value, String domain) {
            this.name = name;
            this.value = value;
            this.domain = domain;
            this.path = "/";
        }

        public String getName() { return name; }
        public String getValue() { return value; }
        public String getDomain() { return domain; }
        public String getPath() { return path; }
        public boolean isSecure() { return secure; }
        public boolean isHttpOnly() { return httpOnly; }
        public long getExpirationDate() { return expirationDate; }

        public void setName(String name) { this.name = name; }
        public void setValue(String value) { this.value = value; }
        public void setDomain(String domain) { this.domain = domain; }
        public void setPath(String path) { this.path = path; }
        public void setSecure(boolean secure) { this.secure = secure; }
        public void setHttpOnly(boolean httpOnly) { this.httpOnly = httpOnly; }
        public void setExpirationDate(long expirationDate) { this.expirationDate = expirationDate; }
    }
}
