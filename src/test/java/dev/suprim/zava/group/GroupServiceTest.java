package dev.suprim.zava.group;

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

class GroupServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

    private MockWebServer server;
    private GroupService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString().replaceAll("/$", "");
        ServiceMap sm = new ServiceMap();
        sm.addService("group", List.of(base));
        sm.addService("group_poll", List.of(base));
        sm.addService("profile", List.of(base));
        sm.addService("file", List.of(base));
        Context ctx = Context.builder()
                .uid("uid").imei("imei").secretKey(SECRET_KEY)
                .userAgent("UA").language("vi")
                .options(ZavaOptions.defaults())
                .cookieJar(new ZavaCookieJar()).serviceMap(sm).build();
        HttpClient http = new HttpClient(ctx);
        ResponseHandler handler = new ResponseHandler(ctx);
        service = new GroupService(ctx, http, handler);
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

    @Test @DisplayName("changeAvatar POSTs multipart to /api/group/upavatar")
    void changeAvatar() throws Exception {
        enqueueOk();
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xD8};
        service.changeAvatar("g-1", image, 300, 300);

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/api/group/upavatar"));
        assertTrue(req.getHeader("Content-Type").contains("multipart/form-data"));
    }

    @Test @DisplayName("createGroup with null name auto-generates")
    void createGroupNullName() throws Exception {
        enqueueOk();
        service.createGroup(null, List.of("uid1"));
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/group/create/v2"));
    }

    @Test @DisplayName("changeName with empty string uses timestamp")
    void changeNameEmpty() throws Exception {
        enqueueOk();
        service.changeName("g-1", "");
        assertTrue(server.takeRequest().getPath().contains("/api/group/updateinfo"));
    }

    @Test @DisplayName("createGroup throws on empty members")
    void createGroupEmptyMembers() {
        assertThrows(Exception.class, () -> service.createGroup("Name", List.of()));
    }
}
