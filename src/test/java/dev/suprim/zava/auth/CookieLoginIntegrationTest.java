package dev.suprim.zava.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.exception.ZavaAuthException;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.session.Context;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CookieLoginIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    private String baseUrl() {
        return server.url("/api/login").toString();
    }

    private Credentials creds() {
        return Credentials.builder()
                .imei("test-imei-123")
                .cookies(List.of(new Credentials.CookieEntry("zpw_sek", "abc", ".zalo.me")))
                .userAgent("Mozilla/5.0 Test")
                .language("vi")
                .build();
    }

    @Test @DisplayName("full login flow succeeds with mocked responses")
    void fullLoginFlow() throws Exception {
        // The tricky part: getLoginInfo encrypts params with ParamsEncryptor,
        // and we need to return a response encrypted with that same key.
        // Since we can't predict the key, we use a Dispatcher that reads the request,
        // extracts the encryptKey from zcid/zcid_ext, and encrypts the response.

        // For simplicity, we return a response that CookieLogin decrypts with the
        // ParamsEncryptor's encryptKey. Since we control the server, we just need
        // the response to be valid.

        // Approach: use a Dispatcher that returns login info and server info
        String secretKey = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path.contains("/getLoginInfo")) {
                    // We need to encrypt with the ParamsEncryptor key,
                    // but we don't know it. Instead, return a response that
                    // the code will try to decrypt. Since we can't predict the key,
                    // this test verifies the HTTP flow, not the decryption.
                    // To make this work, we'd need to extract zcid_ext from the URL params.

                    // Actually, let's extract the encrypt key from URL params
                    try {
                        String url = request.getPath();
                        // The params are AES-encrypted, and we need the encryptKey to decrypt
                        // For testing, we can use a trick: make the response be an error
                        // that exercises the error path, OR we can compute the key.

                        // Simplest: return outer error to test error handling
                        // But we want to test the happy path. Let's do both.

                        // Build inner response
                        ObjectNode innerData = MAPPER.createObjectNode();
                        innerData.put("zpw_enk", secretKey);
                        innerData.put("uid", "test-uid-999");

                        ObjectNode serviceMap = MAPPER.createObjectNode();
                        ArrayNode chatUrls = serviceMap.putArray("chat");
                        chatUrls.add("https://chat.zalo.me");
                        ArrayNode groupUrls = serviceMap.putArray("group");
                        groupUrls.add("https://group.zalo.me");
                        innerData.set("zpw_service_map_v3", serviceMap);

                        ArrayNode wsUrls = innerData.putArray("zpw_ws");
                        wsUrls.add("wss://ws.zalo.me/ws");

                        ObjectNode innerEnvelope = MAPPER.createObjectNode();
                        innerEnvelope.put("error_code", 0);
                        innerEnvelope.set("data", innerData);

                        // We need to encrypt this with the ParamsEncryptor's encryptKey.
                        // Extract zcid_ext from URL to compute the key.
                        // URL has zcid_ext=<value>&zcid=<value>
                        String zcidExt = extractParam(url, "zcid_ext");
                        String zcid = extractParam(url, "zcid");

                        if (zcidExt != null && zcid != null) {
                            String encryptKey = dev.suprim.zava.internal.crypto.ParamsEncryptor
                                    .createEncryptKey(zcidExt, zcid);
                            String encrypted = AesCbc.encodeLogin(encryptKey,
                                    MAPPER.writeValueAsString(innerEnvelope), false, false);
                            return new MockResponse()
                                    .setBody("{\"data\":\"" + encrypted + "\"}")
                                    .setHeader("Content-Type", "application/json");
                        }

                        return new MockResponse().setResponseCode(500);
                    } catch (Exception e) {
                        return new MockResponse().setResponseCode(500).setBody(e.getMessage());
                    }
                }

                if (path.contains("/getServerInfo")) {
                    try {
                        ObjectNode settings = MAPPER.createObjectNode();
                        ObjectNode features = settings.putObject("features");
                        ObjectNode sharefile = features.putObject("sharefile");
                        sharefile.put("chunk_size_file", 524288);
                        sharefile.put("max_file", 20);
                        ObjectNode socket = features.putObject("socket");
                        socket.put("ping_interval", 30000);

                        ObjectNode data = MAPPER.createObjectNode();
                        data.set("setttings", settings); // Zalo typo

                        ObjectNode envelope = MAPPER.createObjectNode();
                        envelope.put("error_code", 0);
                        envelope.set("data", data);

                        return new MockResponse()
                                .setBody(MAPPER.writeValueAsString(envelope))
                                .setHeader("Content-Type", "application/json");
                    } catch (Exception e) {
                        return new MockResponse().setResponseCode(500);
                    }
                }

                return new MockResponse().setResponseCode(404);
            }
        });

        ZavaOptions options = ZavaOptions.builder()
                .loginBaseUrl(baseUrl())
                .build();

        CookieLogin login = new CookieLogin(options);
        Context ctx = login.login(creds());

        assertEquals("test-uid-999", ctx.getUid());
        assertEquals(secretKey, ctx.getSecretKey());
        assertNotNull(ctx.getServiceMap());
        assertTrue(ctx.getServiceMap().hasService("chat"));
        assertNotNull(ctx.getSettings());
        assertNotNull(ctx.getWsUrls());
    }

    @Test @DisplayName("login throws on getLoginInfo HTTP error")
    void loginHttpError() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"error_code\":0,\"data\":{}}"));

        ZavaOptions options = ZavaOptions.builder().loginBaseUrl(baseUrl()).build();
        CookieLogin login = new CookieLogin(options);
        assertThrows(ZavaAuthException.class, () -> login.login(creds()));
    }

    @Test @DisplayName("login throws on missing data in getLoginInfo")
    void loginMissingData() {
        server.enqueue(new MockResponse()
                .setBody("{\"error_code\":-1,\"error_message\":\"Invalid session\"}")
                .setHeader("Content-Type", "application/json"));

        ZavaOptions options = ZavaOptions.builder().loginBaseUrl(baseUrl()).build();
        CookieLogin login = new CookieLogin(options);
        assertThrows(ZavaAuthException.class, () -> login.login(creds()));
    }

    private static String extractParam(String url, String param) {
        int start = url.indexOf(param + "=");
        if (start < 0) return null;
        start += param.length() + 1;
        int end = url.indexOf("&", start);
        if (end < 0) end = url.length();
        try {
            return java.net.URLDecoder.decode(url.substring(start, end), "UTF-8");
        } catch (Exception e) {
            return url.substring(start, end);
        }
    }
}
