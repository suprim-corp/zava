package dev.suprim.zava.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.exception.ZavaAuthException;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Constants;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QR code login flow.
 *
 * <p>Multi-step process:
 * <ol>
 *   <li>Load login page → extract JS version</li>
 *   <li>getLoginInfo → verifyClient</li>
 *   <li>Generate QR code</li>
 *   <li>Long-poll waitingScan</li>
 *   <li>Long-poll waitingConfirm</li>
 *   <li>checkSession → getUserInfo</li>
 *   <li>Return cookies + user info</li>
 * </ol>
 *
 * <p>Equivalent to zca-js {@code loginQR()} in {@code apis/loginQR.ts}.
 */
public class QrLogin {

    private static final Logger log = LoggerFactory.getLogger(QrLogin.class);
    private static final ObjectMapper MAPPER = ResponseHandler.mapper();
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("https://stc-zlogin\\.zdn\\.vn/main-([\\d.]+)\\.js");

    private static final String ID_ZALO = "https://id.zalo.me";
    private static final String CONTINUE_URL = "https://chat.zalo.me/";
    private static final MediaType FORM_URL = MediaType.parse("application/x-www-form-urlencoded");

    private final OkHttpClient client;
    private final CookieJar cookieJar;

    private volatile boolean aborted = false;

    public QrLogin() {
        // Shared cookie jar for the entire QR flow
        dev.suprim.zava.internal.http.ZavaCookieJar zavaCookieJar = new dev.suprim.zava.internal.http.ZavaCookieJar();
        this.cookieJar = zavaCookieJar;
        this.client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS) // Long-poll needs long timeout
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    /**
     * Execute the QR login flow.
     *
     * @param userAgent the user agent string
     * @param language  the language (default "vi")
     * @param callback  callback for QR events (Generated, Scanned, Confirmed, etc.)
     * @return QR login result containing cookies, user info, and generated IMEI
     */
    public QrLoginResult login(String userAgent, String language, Consumer<QrEvent> callback) {
        if (userAgent == null) userAgent = Constants.DEFAULT_USER_AGENT;
        if (language == null) language = "vi";

        try {
            // 1. Load login page → extract JS version
            String version = loadLoginPage(userAgent);
            if (version == null) throw new ZavaAuthException("Cannot extract login version from page");
            log.info("Got login version: {}", version);

            // 2. getLoginInfo + verifyClient
            postForm(userAgent, ID_ZALO + "/account/logininfo",
                    "continue=https://zalo.me/pc&v=" + version);
            postForm(userAgent, ID_ZALO + "/account/verify-client",
                    "type=device&continue=https://zalo.me/pc&v=" + version);

            // 3. Generate QR
            JsonNode qrResult = postFormJson(userAgent, ID_ZALO + "/account/authen/qr/generate",
                    "continue=https://zalo.me/pc&v=" + version);

            if (qrResult == null || qrResult.path("data").isNull()) {
                throw new ZavaAuthException("Failed to generate QR code");
            }

            JsonNode qrData = qrResult.path("data");
            String code = qrData.path("code").asText();
            String imageBase64 = qrData.path("image").asText("")
                    .replace("data:image/png;base64,", "");

            // Notify callback
            if (callback != null) {
                callback.accept(new QrEvent(QrEventType.QR_GENERATED, qrData));
            }

            // Save QR to file
            Path qrPath = Path.of("qr.png");
            Files.write(qrPath, Base64.getDecoder().decode(imageBase64));
            log.info("QR code saved to '{}'. Scan to proceed.", qrPath);

            // 4. Waiting scan (long-poll, recursive on error_code=8)
            checkAborted();
            JsonNode scanResult = waitingScan(userAgent, version, code);
            if (scanResult == null || scanResult.path("data").isNull()) {
                throw new ZavaAuthException("QR scan failed or timed out");
            }

            if (callback != null) {
                callback.accept(new QrEvent(QrEventType.QR_SCANNED, scanResult.path("data")));
            }

            // 5. Waiting confirm (long-poll)
            checkAborted();
            log.info("Please confirm on your phone");
            JsonNode confirmResult = waitingConfirm(userAgent, version, code);
            if (confirmResult == null) {
                throw new ZavaAuthException("QR confirm failed");
            }

            int confirmCode = confirmResult.path("error_code").asInt(-1);
            if (confirmCode == -13) {
                if (callback != null) {
                    callback.accept(new QrEvent(QrEventType.QR_DECLINED, confirmResult));
                }
                throw new ZavaAuthException("QR login declined by user");
            }
            if (confirmCode != 0) {
                throw new ZavaAuthException("QR confirm error: " + confirmResult);
            }

            // 6. Check session
            checkSession(userAgent);

            // 7. Get user info
            JsonNode userInfo = getUserInfo(userAgent);
            if (userInfo == null || !userInfo.path("data").path("logged").asBoolean(false)) {
                throw new ZavaAuthException("QR login session validation failed");
            }

            log.info("Successfully logged in as {}",
                    userInfo.path("data").path("info").path("name").asText("unknown"));

            if (callback != null) {
                callback.accept(new QrEvent(QrEventType.LOGIN_SUCCESS, userInfo.path("data")));
            }

            // 8. Extract cookies
            HttpUrl chatUrl = HttpUrl.parse("https://chat.zalo.me/");
            List<Cookie> cookies = cookieJar.loadForRequest(chatUrl);

            // Also collect cookies from id.zalo.me
            HttpUrl idUrl = HttpUrl.parse("https://id.zalo.me/");
            cookies = new java.util.ArrayList<>(cookies);
            cookies.addAll(cookieJar.loadForRequest(idUrl));

            // Build credentials
            String imei = dev.suprim.zava.internal.crypto.Hashing.generateUUID(userAgent);

            return new QrLoginResult(
                    cookies,
                    userInfo.path("data").path("info"),
                    imei,
                    userAgent,
                    language);

        } catch (ZavaAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaAuthException("QR login failed", e);
        }
    }

    /** Abort the QR login flow. */
    public void abort() {
        this.aborted = true;
    }

    // ── Steps ────────────────────────────────────────────────────────────

    private String loadLoginPage(String userAgent) throws IOException {
        Request request = new Request.Builder()
                .url(ID_ZALO + "/account?continue=https%3A%2F%2Fchat.zalo.me%2F")
                .header("User-Agent", userAgent)
                .header("Accept", "text/html")
                .header("Referer", "https://chat.zalo.me/")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String html = response.body() != null ? response.body().string() : "";
            Matcher matcher = VERSION_PATTERN.matcher(html);
            return matcher.find() ? matcher.group(1) : null;
        }
    }

    private void postForm(String userAgent, String url, String formBody) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", ID_ZALO + "/account?continue=https%3A%2F%2Fzalo.me%2Fpc")
                .post(RequestBody.create(formBody, FORM_URL))
                .build();

        try (Response response = client.newCall(request).execute()) {
            // consume response
        }
    }

    private JsonNode postFormJson(String userAgent, String url, String formBody) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", ID_ZALO + "/account?continue=https%3A%2F%2Fzalo.me%2Fpc")
                .post(RequestBody.create(formBody, FORM_URL))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            return MAPPER.readTree(body);
        }
    }

    private JsonNode waitingScan(String userAgent, String version, String code) throws IOException {
        checkAborted();

        String formBody = "code=" + code + "&continue=https://chat.zalo.me/&v=" + version;
        JsonNode result = postFormJson(userAgent,
                ID_ZALO + "/account/authen/qr/waiting-scan", formBody);

        if (result != null && result.path("error_code").asInt(-1) == 8) {
            // Timeout, retry
            return waitingScan(userAgent, version, code);
        }

        return result;
    }

    private JsonNode waitingConfirm(String userAgent, String version, String code) throws IOException {
        checkAborted();

        String formBody = "code=" + code
                + "&gToken=&gAction=CONFIRM_QR"
                + "&continue=https://chat.zalo.me/&v=" + version;
        JsonNode result = postFormJson(userAgent,
                ID_ZALO + "/account/authen/qr/waiting-confirm", formBody);

        if (result != null && result.path("error_code").asInt(-1) == 8) {
            return waitingConfirm(userAgent, version, code);
        }

        return result;
    }

    private void checkSession(String userAgent) throws IOException {
        Request request = new Request.Builder()
                .url(ID_ZALO + "/account/checksession?continue=https%3A%2F%2Fchat.zalo.me%2Findex.html")
                .header("User-Agent", userAgent)
                .header("Referer", ID_ZALO + "/account?continue=https%3A%2F%2Fchat.zalo.me%2F")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            // consume — redirect sets cookies
        }
    }

    private JsonNode getUserInfo(String userAgent) throws IOException {
        Request request = new Request.Builder()
                .url(Constants.USER_INFO_URL)
                .header("User-Agent", userAgent)
                .header("Referer", "https://chat.zalo.me/")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            return MAPPER.readTree(body);
        }
    }

    private void checkAborted() {
        if (aborted) throw new ZavaAuthException("QR login aborted");
    }

    // ── Result types ─────────────────────────────────────────────────────

    public enum QrEventType {
        QR_GENERATED,
        QR_SCANNED,
        QR_DECLINED,
        LOGIN_SUCCESS
    }

    public static class QrEvent {
        private final QrEventType type;
        private final JsonNode data;

        public QrEvent(QrEventType type, JsonNode data) {
            this.type = type;
            this.data = data;
        }

        public QrEventType getType() { return type; }
        public JsonNode getData() { return data; }
    }

    public static class QrLoginResult {
        private final List<Cookie> cookies;
        private final JsonNode userInfo;
        private final String imei;
        private final String userAgent;
        private final String language;

        public QrLoginResult(List<Cookie> cookies, JsonNode userInfo,
                             String imei, String userAgent, String language) {
            this.cookies = cookies;
            this.userInfo = userInfo;
            this.imei = imei;
            this.userAgent = userAgent;
            this.language = language;
        }

        public List<Cookie> getCookies() { return cookies; }
        public JsonNode getUserInfo() { return userInfo; }
        public String getImei() { return imei; }
        public String getUserAgent() { return userAgent; }
        public String getLanguage() { return language; }

        /**
         * Convert to Credentials for use with {@code Zava.login()}.
         */
        public Credentials toCredentials() {
            java.util.List<Credentials.CookieEntry> entries = new java.util.ArrayList<>();
            for (Cookie c : cookies) {
                Credentials.CookieEntry entry = new Credentials.CookieEntry(
                        c.name(), c.value(), c.domain());
                entry.setPath(c.path());
                entry.setSecure(c.secure());
                entry.setHttpOnly(c.httpOnly());
                entry.setExpirationDate(c.expiresAt() / 1000);
                entries.add(entry);
            }

            return Credentials.builder()
                    .imei(imei)
                    .cookies(entries)
                    .userAgent(userAgent)
                    .language(language)
                    .build();
        }
    }
}
