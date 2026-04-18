package dev.suprim.zava.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.exception.ZavaAuthException;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.crypto.ParamsEncryptor;
import dev.suprim.zava.internal.crypto.Signer;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.http.Urls;
import dev.suprim.zava.internal.http.ZavaCookieJar;
import dev.suprim.zava.internal.session.Constants;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.session.ServiceMap;
import dev.suprim.zava.internal.session.Settings;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cookie-based login flow.
 *
 * <p>Equivalent to zca-js {@code login()} + {@code getServerInfo()} in {@code apis/login.ts}
 * and {@code loginCookie()} in {@code zalo.ts}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Parse cookies into OkHttp CookieJar</li>
 *   <li>Encrypt login params (ParamsEncryptor)</li>
 *   <li>Call {@code getLoginInfo} → get secretKey, uid, service map</li>
 *   <li>Call {@code getServerInfo} → get settings, WS URLs</li>
 *   <li>Build and return {@link Context}</li>
 * </ol>
 */
public class CookieLogin {

    private static final Logger log = LoggerFactory.getLogger(CookieLogin.class);
    private static final ObjectMapper MAPPER = ResponseHandler.mapper();

    private final ZavaOptions options;

    public CookieLogin(ZavaOptions options) {
        this.options = options;
    }

    /**
     * Execute the login flow.
     *
     * @param credentials user-provided credentials
     * @return fully-initialized session Context
     * @throws ZavaAuthException if login fails
     */
    public Context login(Credentials credentials) {
        // 1. Validate
        if (credentials.getImei() == null || credentials.getImei().isEmpty()) {
            throw new ZavaAuthException("Missing IMEI");
        }
        if (credentials.getCookies() == null || credentials.getCookies().isEmpty()) {
            throw new ZavaAuthException("Missing cookies");
        }
        if (credentials.getUserAgent() == null || credentials.getUserAgent().isEmpty()) {
            throw new ZavaAuthException("Missing user agent");
        }

        // 2. Parse cookies
        ZavaCookieJar cookieJar = parseCookies(credentials.getCookies());

        // 3. Build a temporary context for the login HTTP calls
        Context tempCtx = Context.builder()
                .imei(credentials.getImei())
                .userAgent(credentials.getUserAgent())
                .language(credentials.getLanguage())
                .cookieJar(cookieJar)
                .options(options)
                .serviceMap(new ServiceMap())
                .build();

        HttpClient http = new HttpClient(tempCtx);

        try {
            // 4. getLoginInfo
            LoginResult loginResult = getLoginInfo(http, tempCtx);

            // 5. getServerInfo
            ServerInfoResult serverInfo = getServerInfo(http, tempCtx);

            // 6. Build the real context
            return Context.builder()
                    .uid(loginResult.uid)
                    .imei(credentials.getImei())
                    .secretKey(loginResult.secretKey)
                    .userAgent(credentials.getUserAgent())
                    .language(credentials.getLanguage())
                    .cookieJar(cookieJar)
                    .serviceMap(loginResult.serviceMap)
                    .settings(serverInfo.settings)
                    .options(options)
                    .wsUrls(loginResult.wsUrls)
                    .build();

        } catch (ZavaAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaAuthException("Login failed", e);
        }
    }

    // ── getLoginInfo ─────────────────────────────────────────────────────

    private LoginResult getLoginInfo(HttpClient http, Context ctx) throws IOException {
        // Build encrypted params
        ParamsEncryptor encryptor = new ParamsEncryptor(
                options.getApiType(),
                ctx.getImei(),
                System.currentTimeMillis());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("computer_name", "Web");
        data.put("imei", ctx.getImei());
        data.put("language", ctx.getLanguage());
        data.put("ts", System.currentTimeMillis());

        String encryptedData = encryptor.encrypt(MAPPER.writeValueAsString(data));
        String encryptKey = encryptor.getEncryptKey();
        Map<String, String> encryptorParams = encryptor.getParams();

        // Build URL params
        Map<String, Object> urlParams = new LinkedHashMap<>();
        urlParams.putAll(encryptorParams);
        urlParams.put("params", encryptedData);
        urlParams.put("type", options.getApiType());
        urlParams.put("client_version", options.getApiVersion());

        // Sign key: for getlogininfo, sign ALL params
        urlParams.put("signkey", Signer.getSignKey("getlogininfo", urlParams));
        urlParams.put("nretry", 0);

        String url = Urls.build(
                options.getLoginBaseUrl() + "/getLoginInfo",
                urlParams, true, ctx);

        Response response = http.get(url);

        try (response) {
            if (!response.isSuccessful()) {
                throw new ZavaAuthException("getLoginInfo failed: HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new ZavaAuthException("getLoginInfo: empty response");
            }

            JsonNode envelope = MAPPER.readTree(body.string());
            JsonNode dataField = envelope.path("data");

            if (dataField.isMissingNode() || dataField.isNull()) {
                String errorMsg = envelope.path("error_message").asText("Unknown error");
                throw new ZavaAuthException("getLoginInfo failed: " + errorMsg);
            }

            // Decrypt with the ParamsEncryptor key
            String decrypted = AesCbc.decodeLogin(encryptKey, dataField.asText());
            JsonNode loginData = MAPPER.readTree(decrypted);

            // Check for nested "data" field
            JsonNode innerData = loginData.has("data") ? loginData.path("data") : loginData;

            LoginResult result = new LoginResult();
            result.secretKey = innerData.path("zpw_enk").asText(null);
            result.uid = innerData.path("uid").asText(null);

            if (result.secretKey == null || result.uid == null) {
                throw new ZavaAuthException("getLoginInfo: missing zpw_enk or uid in response");
            }

            // Parse service map
            JsonNode serviceMapNode = innerData.path("zpw_service_map_v3");
            if (!serviceMapNode.isMissingNode()) {
                result.serviceMap = MAPPER.treeToValue(serviceMapNode, ServiceMap.class);
            } else {
                result.serviceMap = new ServiceMap();
            }

            // Parse WS URLs
            JsonNode wsNode = innerData.path("zpw_ws");
            if (wsNode.isArray()) {
                result.wsUrls = new ArrayList<>();
                for (JsonNode urlNode : wsNode) {
                    result.wsUrls.add(urlNode.asText());
                }
            }

            log.info("Logged in as {}", result.uid);
            return result;
        }
    }

    // ── getServerInfo ────────────────────────────────────────────────────

    private ServerInfoResult getServerInfo(HttpClient http, Context ctx) throws IOException {
        // Sign key for getserverinfo uses fixed params
        Map<String, Object> signParams = new LinkedHashMap<>();
        signParams.put("imei", ctx.getImei());
        signParams.put("type", options.getApiType());
        signParams.put("client_version", options.getApiVersion());
        signParams.put("computer_name", "Web");

        String signKey = Signer.getSignKey("getserverinfo", signParams);

        Map<String, Object> urlParams = new LinkedHashMap<>(signParams);
        urlParams.put("signkey", signKey);

        String url = Urls.build(
                options.getLoginBaseUrl() + "/getServerInfo",
                urlParams, false, ctx);

        Response response = http.get(url);

        try (response) {
            if (!response.isSuccessful()) {
                throw new ZavaAuthException("getServerInfo failed: HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new ZavaAuthException("getServerInfo: empty response");
            }

            JsonNode envelope = MAPPER.readTree(body.string());
            JsonNode dataField = envelope.path("data");

            if (dataField.isMissingNode() || dataField.isNull()) {
                String errorMsg = envelope.path("error_message").asText("Unknown error");
                throw new ZavaAuthException("getServerInfo failed: " + errorMsg);
            }

            ServerInfoResult result = new ServerInfoResult();

            // Zalo has a typo: "setttings" (3 t's) — handle both
            JsonNode settingsNode = dataField.has("setttings")
                    ? dataField.path("setttings")
                    : dataField.path("settings");

            if (!settingsNode.isMissingNode()) {
                result.settings = MAPPER.treeToValue(settingsNode, Settings.class);
            }

            return result;
        }
    }

    // ── Cookie parsing ───────────────────────────────────────────────────

    private ZavaCookieJar parseCookies(List<Credentials.CookieEntry> entries) {
        ZavaCookieJar jar = new ZavaCookieJar();
        HttpUrl defaultUrl = HttpUrl.parse(Constants.ORIGIN + "/");

        for (Credentials.CookieEntry entry : entries) {
            String domain = entry.getDomain();
            if (domain != null && domain.startsWith(".")) {
                domain = domain.substring(1);
            }
            if (domain == null || domain.isEmpty()) {
                domain = "chat.zalo.me";
            }

            HttpUrl cookieUrl = HttpUrl.parse("https://" + domain + "/");
            if (cookieUrl == null) {
                cookieUrl = defaultUrl;
            }

            // Build cookie string for OkHttp parsing
            String cookieStr = entry.getName() + "=" + entry.getValue();
            jar.addCookies(cookieUrl, List.of(cookieStr));
        }

        return jar;
    }

    // ── Internal result types ────────────────────────────────────────────

    private static class LoginResult {
        String secretKey;
        String uid;
        ServiceMap serviceMap;
        List<String> wsUrls;
    }

    private static class ServerInfoResult {
        Settings settings;
    }
}
