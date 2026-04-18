package dev.suprim.zava.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.http.ZavaCookieJar;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.session.ServiceMap;
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

class ProfileServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

    private MockWebServer server;
    private ProfileService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString().replaceAll("/$", "");
        ServiceMap sm = new ServiceMap();
        sm.addService("profile", List.of(base));
        sm.addService("file", List.of(base));
        Context ctx = Context.builder()
                .uid("uid").imei("imei").secretKey(SECRET_KEY)
                .userAgent("UA").language("vi")
                .options(ZavaOptions.defaults())
                .cookieJar(new ZavaCookieJar()).serviceMap(sm).build();
        HttpClient http = new HttpClient(ctx);
        ResponseHandler handler = new ResponseHandler(ctx);
        service = new ProfileService(ctx, http, handler);
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    private void enqueueOk() throws Exception {
        String inner = "{\"error_code\":0,\"data\":{\"status\":0}}";
        String enc = AesCbc.encodeAES(SECRET_KEY, inner);
        server.enqueue(new MockResponse()
                .setBody("{\"error_code\":0,\"data\":\"" + enc + "\"}")
                .setHeader("Content-Type", "application/json"));
    }

    @Test @DisplayName("changeAvatar POSTs multipart to /api/profile/upavatar")
    void changeAvatar() throws Exception {
        enqueueOk();
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}; // JPEG header
        service.changeAvatar(image, 200, 200);

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/api/profile/upavatar"));
        assertTrue(req.getHeader("Content-Type").contains("multipart/form-data"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("fileContent"));
        assertTrue(body.contains("params"));
    }

    @Test @DisplayName("fetchAccountInfo GETs /api/social/profile/me-v2")
    void fetchAccountInfo() throws Exception {
        enqueueOk();
        service.fetchAccountInfo();
        assertTrue(server.takeRequest().getPath().contains("/api/social/profile/me-v2"));
    }

    @Test @DisplayName("updateBio POSTs")
    void updateBio() throws Exception {
        enqueueOk();
        service.updateBio("Hello");
        assertTrue(server.takeRequest().getPath().contains("/api/social/profile/update"));
    }
}
