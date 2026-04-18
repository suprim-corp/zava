package dev.suprim.zava.internal.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.exception.ZavaException;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.session.ServiceMap;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BaseService, ResponseHandler edge cases, and HttpClient.
 */
class HttpLayerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

    private MockWebServer server;
    private Context context;
    private HttpClient http;
    private ResponseHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString().replaceAll("/$", "");
        ServiceMap sm = new ServiceMap();
        sm.addService("test", List.of(base));
        context = Context.builder()
                .uid("uid").imei("imei").secretKey(SECRET_KEY)
                .userAgent("UA").language("vi")
                .options(ZavaOptions.defaults())
                .cookieJar(new ZavaCookieJar()).serviceMap(sm).build();
        http = new HttpClient(context);
        handler = new ResponseHandler(context);
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    private void enqueueEncrypted(Object data) throws Exception {
        String inner = MAPPER.writeValueAsString(MAPPER.createObjectNode()
                .put("error_code", 0).set("data", MAPPER.valueToTree(data)));
        String enc = AesCbc.encodeAES(SECRET_KEY, inner);
        server.enqueue(new MockResponse()
                .setBody("{\"error_code\":0,\"data\":\"" + enc + "\"}")
                .setHeader("Content-Type", "application/json"));
    }

    private void enqueueUnencrypted(Object data) throws Exception {
        server.enqueue(new MockResponse()
                .setBody(MAPPER.writeValueAsString(MAPPER.createObjectNode()
                        .put("error_code", 0).set("data", MAPPER.valueToTree(data))))
                .setHeader("Content-Type", "application/json"));
    }

    // ── ResponseHandler edge cases ───────────────────────────────────────

    @Test @DisplayName("handleRaw encrypted returns JsonNode")
    void handleRawEncrypted() throws Exception {
        enqueueEncrypted(MAPPER.createObjectNode().put("key", "val"));
        var resp = http.get(server.url("/test").toString());
        JsonNode node = handler.handleRaw(resp, true);
        assertEquals("val", node.path("key").asText());
    }

    @Test @DisplayName("handleRaw unencrypted returns data directly")
    void handleRawUnencrypted() throws Exception {
        enqueueUnencrypted(MAPPER.createObjectNode().put("k", "v"));
        var resp = http.get(server.url("/test").toString());
        JsonNode node = handler.handleRaw(resp, false);
        assertEquals("v", node.path("k").asText());
    }

    @Test @DisplayName("handle with TypeReference")
    void handleTypeReference() throws Exception {
        enqueueEncrypted(MAPPER.createObjectNode().put("a", 1).put("b", 2));
        var resp = http.get(server.url("/test").toString());
        Map<String, Integer> map = handler.handle(resp, new TypeReference<Map<String, Integer>>() {});
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
    }

    @Test @DisplayName("handle with TypeReference unencrypted")
    void handleTypeReferenceUnencrypted() throws Exception {
        enqueueUnencrypted(MAPPER.createObjectNode().put("x", 99));
        var resp = http.get(server.url("/test").toString());
        Map<String, Integer> map = handler.handle(resp, new TypeReference<Map<String, Integer>>() {}, false);
        assertEquals(99, map.get("x"));
    }

    @Test @DisplayName("handle throws on null data field")
    void handleNullData() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"error_code\":0}")
                .setHeader("Content-Type", "application/json"));
        var resp = http.get(server.url("/test").toString());
        assertThrows(ZavaException.class, () -> handler.handle(resp, JsonNode.class));
    }

    @Test @DisplayName("handle throws on empty body")
    void handleEmptyBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));
        var resp = http.get(server.url("/test").toString());
        assertThrows(ZavaException.class, () -> handler.handle(resp, JsonNode.class));
    }

    @Test @DisplayName("handle inner error_code != 0 throws")
    void handleInnerError() throws Exception {
        String inner = "{\"error_code\":-999,\"error_message\":\"inner fail\"}";
        String enc = AesCbc.encodeAES(SECRET_KEY, inner);
        server.enqueue(new MockResponse()
                .setBody("{\"error_code\":0,\"data\":\"" + enc + "\"}")
                .setHeader("Content-Type", "application/json"));
        var resp = http.get(server.url("/test").toString());
        ZavaException ex = assertThrows(ZavaException.class, () -> handler.handle(resp, JsonNode.class));
        assertEquals(-999, ex.getCode());
    }

    // ── HttpClient edge cases ────────────────────────────────────────────

    @Test @DisplayName("HttpClient follows redirect manually")
    void httpRedirect() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302)
                .setHeader("Location", server.url("/redirected").toString()));
        enqueueUnencrypted(MAPPER.createObjectNode().put("ok", true));

        var resp = http.get(server.url("/original").toString());
        JsonNode node = handler.handleRaw(resp, false);
        assertTrue(node.path("ok").asBoolean());
    }

    @Test @DisplayName("HttpClient POST with raw body")
    void httpPostRawBody() throws Exception {
        enqueueUnencrypted(MAPPER.createObjectNode().put("ok", true));
        var resp = http.post(server.url("/test").toString(), "{\"a\":1}",
                okhttp3.MediaType.parse("application/json"));
        assertNotNull(resp);
        resp.close();
    }

    // ── BaseService via concrete subclass ────────────────────────────────

    /** Minimal BaseService subclass for testing. */
    static class TestService extends BaseService {
        TestService(Context ctx, HttpClient http, ResponseHandler handler) {
            super(ctx, http, handler);
        }

        JsonNode testEncryptedGetRaw(Map<String, Object> params) {
            return encryptedGetRaw("test", "/api/test", params);
        }

        JsonNode testSimpleGetRaw() {
            return simpleGetRaw("test", "/api/simple");
        }

        JsonNode testSimpleGetUnencrypted() {
            return simpleGetUnencrypted("test", "/api/unenc");
        }

        JsonNode testEncryptedPostRaw(Map<String, Object> params) {
            return encryptedPostRaw("test", "/api/post", params);
        }

        <T> T testEncryptedGet(Map<String, Object> params, Class<T> type) {
            return encryptedGet("test", "/api/get", params, type);
        }

        <T> T testEncryptedPost(Map<String, Object> params, Class<T> type) {
            return encryptedPost("test", "/api/post", params, type);
        }
    }

    @Test @DisplayName("encryptedGetRaw works")
    void encryptedGetRaw() throws Exception {
        enqueueEncrypted(MAPPER.createObjectNode().put("r", "get"));
        TestService svc = new TestService(context, http, handler);
        JsonNode result = svc.testEncryptedGetRaw(Map.of("p", "v"));
        assertEquals("get", result.path("r").asText());
        assertTrue(server.takeRequest().getPath().contains("params="));
    }

    @Test @DisplayName("simpleGetRaw works")
    void simpleGetRaw() throws Exception {
        enqueueEncrypted(MAPPER.createObjectNode().put("r", "simple"));
        TestService svc = new TestService(context, http, handler);
        JsonNode result = svc.testSimpleGetRaw();
        assertEquals("simple", result.path("r").asText());
    }

    @Test @DisplayName("simpleGetUnencrypted works")
    void simpleGetUnencrypted() throws Exception {
        enqueueUnencrypted(MAPPER.createObjectNode().put("r", "plain"));
        TestService svc = new TestService(context, http, handler);
        JsonNode result = svc.testSimpleGetUnencrypted();
        assertEquals("plain", result.path("r").asText());
    }

    @Test @DisplayName("encryptedPost typed works")
    void encryptedPostTyped() throws Exception {
        enqueueEncrypted(MAPPER.createObjectNode().put("val", 42));
        TestService svc = new TestService(context, http, handler);
        JsonNode result = svc.testEncryptedPost(Map.of("a", "b"), JsonNode.class);
        assertEquals(42, result.path("val").asInt());
    }

    @Test @DisplayName("encryptedGet typed works")
    void encryptedGetTyped() throws Exception {
        enqueueEncrypted(MAPPER.createObjectNode().put("val", 99));
        TestService svc = new TestService(context, http, handler);
        JsonNode result = svc.testEncryptedGet(Map.of("x", "y"), JsonNode.class);
        assertEquals(99, result.path("val").asInt());
    }
}
