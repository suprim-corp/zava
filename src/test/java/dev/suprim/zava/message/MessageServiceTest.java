package dev.suprim.zava.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.conversation.ThreadType;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

    private MockWebServer server;
    private MessageService messageService;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        String baseUrl = server.url("/").toString();
        // Remove trailing slash
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        ServiceMap serviceMap = new ServiceMap();
        serviceMap.addService("chat", List.of(baseUrl));
        serviceMap.addService("group", List.of(baseUrl));
        serviceMap.addService("file", List.of(baseUrl));

        Context context = Context.builder()
                .uid("my-uid-123")
                .imei("test-imei")
                .secretKey(SECRET_KEY)
                .userAgent("Mozilla/5.0 Test")
                .language("vi")
                .options(ZavaOptions.defaults())
                .cookieJar(new ZavaCookieJar())
                .serviceMap(serviceMap)
                .build();

        HttpClient http = new HttpClient(context);
        ResponseHandler handler = new ResponseHandler(context);
        messageService = new MessageService(context, http, handler);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueueSuccess(Object data) throws Exception {
        String innerJson = MAPPER.writeValueAsString(
                MAPPER.createObjectNode().put("error_code", 0).set("data",
                        MAPPER.valueToTree(data)));
        String encrypted = AesCbc.encodeAES(SECRET_KEY, innerJson);
        String envelope = MAPPER.writeValueAsString(
                MAPPER.createObjectNode().put("error_code", 0).put("data", encrypted));
        server.enqueue(new MockResponse()
                .setBody(envelope)
                .setHeader("Content-Type", "application/json"));
    }

    @Test
    @DisplayName("send text to user")
    void sendToUser() throws Exception {
        enqueueSuccess(MAPPER.createObjectNode().put("msgId", 12345));

        SendResult result = messageService.send("Hello!", "user-123", ThreadType.USER);
        assertEquals(12345, result.getMsgId());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/api/message/sms"));
        assertTrue(req.getPath().contains("zpw_ver="));

        // Verify body contains encrypted params
        String body = req.getBody().readUtf8();
        assertTrue(body.startsWith("params="));

        // Decrypt and check params
        String encrypted = URLDecoder.decode(body.substring("params=".length()), StandardCharsets.UTF_8.name());
        String decrypted = AesCbc.decodeAES(SECRET_KEY, encrypted);
        JsonNode params = MAPPER.readTree(decrypted);
        assertEquals("Hello!", params.get("message").asText());
        assertEquals("user-123", params.get("toid").asText());
        assertTrue(params.has("imei"));
        assertTrue(params.has("clientId"));
    }

    @Test
    @DisplayName("send text to group")
    void sendToGroup() throws Exception {
        enqueueSuccess(MAPPER.createObjectNode().put("msgId", 67890));

        SendResult result = messageService.send("Hi group!", "group-456", ThreadType.GROUP);
        assertEquals(67890, result.getMsgId());

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/group/sendmsg"));

        String body = req.getBody().readUtf8();
        String encrypted = URLDecoder.decode(body.substring("params=".length()), StandardCharsets.UTF_8.name());
        String decrypted = AesCbc.decodeAES(SECRET_KEY, encrypted);
        JsonNode params = MAPPER.readTree(decrypted);
        assertEquals("Hi group!", params.get("message").asText());
        assertEquals("group-456", params.get("grid").asText());
        assertEquals(0, params.get("visibility").asInt());
    }

    @Test
    @DisplayName("send with mentions uses /mention endpoint")
    void sendWithMentions() throws Exception {
        enqueueSuccess(MAPPER.createObjectNode().put("msgId", 111));

        List<Mention> mentions = List.of(new Mention("uid-1", 0, 5));
        messageService.send("@user hello", "group-1", ThreadType.GROUP, mentions);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/group/mention"));
    }

    @Test
    @DisplayName("send with quote uses /quote endpoint")
    void sendWithQuote() throws Exception {
        enqueueSuccess(MAPPER.createObjectNode().put("msgId", 222));

        Quote quote = Quote.builder()
                .ownerId("owner-1")
                .globalMsgId("msg-123")
                .cliMsgId("cli-123")
                .ts(1700000000000L)
                .msg("original msg")
                .build();

        messageService.send("reply text", "user-1", ThreadType.USER, null, quote, 0);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/message/quote"));
    }

    @Test
    @DisplayName("delete message for user")
    void deleteMessage() throws Exception {
        enqueueSuccess(MAPPER.createObjectNode().put("status", 0));

        messageService.delete("msg-1", "cli-1", "uid-from", "user-1", ThreadType.USER, true);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/message/delete"));
    }

    @Test
    @DisplayName("undo message for group")
    void undoMessage() throws Exception {
        enqueueSuccess(MAPPER.createObjectNode().put("status", 0));

        messageService.undo("msg-1", "cli-1", "group-1", ThreadType.GROUP);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/group/undomsg"));
    }

    @Test
    @DisplayName("forward message to multiple users")
    void forwardMessage() throws Exception {
        enqueueSuccess(MAPPER.createObjectNode());

        messageService.forward("forwarded text", Arrays.asList("user-1", "user-2"),
                ThreadType.USER, "orig-msg-1", 0);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/message/mforward"));
    }

    @Test
    @DisplayName("send throws on null message")
    void sendNullMessage() {
        assertThrows(NullPointerException.class, () ->
                messageService.send(null, "id", ThreadType.USER));
    }

    @Test
    @DisplayName("forward throws on empty targetIds")
    void forwardEmptyTargets() {
        assertThrows(Exception.class, () ->
                messageService.forward("msg", List.of(), ThreadType.USER, "id", 0));
    }
}
