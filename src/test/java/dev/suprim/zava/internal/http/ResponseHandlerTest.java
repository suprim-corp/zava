package dev.suprim.zava.internal.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.exception.ZavaException;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.session.ServiceMap;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ResponseHandlerTest {

    // 16-byte key -> Base64
    private static final String SECRET_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

    private MockWebServer server;
    private OkHttpClient client;
    private ResponseHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        client = new OkHttpClient();

        Context context = Context.builder()
                .secretKey(SECRET_KEY)
                .options(ZavaOptions.defaults())
                .cookieJar(new ZavaCookieJar())
                .serviceMap(new ServiceMap())
                .build();

        handler = new ResponseHandler(context);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("handles encrypted response correctly")
    void handleEncrypted() throws IOException {
        // Inner data
        String innerJson = "{\"error_code\":0,\"data\":{\"uid\":\"12345\",\"name\":\"Test\"}}";
        String encrypted = AesCbc.encodeAES(SECRET_KEY, innerJson);

        // Outer envelope
        String envelope = "{\"error_code\":0,\"error_message\":\"Success\",\"data\":\"" + encrypted + "\"}";
        server.enqueue(new MockResponse().setBody(envelope).setHeader("Content-Type", "application/json"));

        Response response = client.newCall(
                new Request.Builder().url(server.url("/api/test")).build()
        ).execute();

        TestResult result = handler.handle(response, TestResult.class);
        assertEquals("12345", result.uid);
        assertEquals("Test", result.name);
    }

    @Test
    @DisplayName("handles unencrypted response correctly")
    void handleUnencrypted() throws IOException {
        String envelope = "{\"error_code\":0,\"data\":{\"uid\":\"999\",\"name\":\"Plain\"}}";
        server.enqueue(new MockResponse().setBody(envelope).setHeader("Content-Type", "application/json"));

        Response response = client.newCall(
                new Request.Builder().url(server.url("/api/test")).build()
        ).execute();

        TestResult result = handler.handle(response, TestResult.class, false);
        assertEquals("999", result.uid);
        assertEquals("Plain", result.name);
    }

    @Test
    @DisplayName("throws on outer error_code != 0")
    void outerError() throws IOException {
        String envelope = "{\"error_code\":-123,\"error_message\":\"Invalid session\"}";
        server.enqueue(new MockResponse().setBody(envelope).setHeader("Content-Type", "application/json"));

        Response response = client.newCall(
                new Request.Builder().url(server.url("/api/test")).build()
        ).execute();

        ZavaException ex = assertThrows(ZavaException.class,
                () -> handler.handle(response, TestResult.class));
        assertEquals(-123, ex.getCode());
        assertTrue(ex.getMessage().contains("Invalid session"));
    }

    @Test
    @DisplayName("throws on inner error_code != 0")
    void innerError() throws IOException {
        String innerJson = "{\"error_code\":-456,\"error_message\":\"Rate limited\"}";
        String encrypted = AesCbc.encodeAES(SECRET_KEY, innerJson);
        String envelope = "{\"error_code\":0,\"data\":\"" + encrypted + "\"}";
        server.enqueue(new MockResponse().setBody(envelope).setHeader("Content-Type", "application/json"));

        Response response = client.newCall(
                new Request.Builder().url(server.url("/api/test")).build()
        ).execute();

        ZavaException ex = assertThrows(ZavaException.class,
                () -> handler.handle(response, TestResult.class));
        assertEquals(-456, ex.getCode());
        assertTrue(ex.getMessage().contains("Rate limited"));
    }

    @Test
    @DisplayName("throws on HTTP error status")
    void httpError() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        Response response = client.newCall(
                new Request.Builder().url(server.url("/api/test")).build()
        ).execute();

        ZavaException ex = assertThrows(ZavaException.class,
                () -> handler.handle(response, TestResult.class));
        assertEquals(500, ex.getCode());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TestResult {
        @JsonProperty("uid") String uid;
        @JsonProperty("name") String name;
    }
}
