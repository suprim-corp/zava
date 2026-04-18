package dev.suprim.zava.internal.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.conversation.ThreadType;
import dev.suprim.zava.group.GroupService;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.session.ServiceMap;
import dev.suprim.zava.poll.PollService;
import dev.suprim.zava.profile.ProfileService;
import dev.suprim.zava.reaction.ReactionService;
import dev.suprim.zava.settings.SettingsService;
import dev.suprim.zava.sticker.StickerService;
import dev.suprim.zava.user.UserService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for all domain services using MockWebServer.
 * Verifies correct endpoints, HTTP methods, and param encryption.
 */
class DomainServicesTest {

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

        String baseUrl = server.url("/").toString();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        ServiceMap sm = new ServiceMap();
        for (String s : List.of("chat", "group", "group_poll", "friend", "profile",
                "reaction", "sticker", "label", "alias", "file")) {
            sm.addService(s, List.of(baseUrl));
        }

        context = Context.builder()
                .uid("my-uid").imei("test-imei").secretKey(SECRET_KEY)
                .userAgent("UA").language("vi")
                .options(ZavaOptions.defaults())
                .cookieJar(new ZavaCookieJar()).serviceMap(sm).build();

        http = new HttpClient(context);
        handler = new ResponseHandler(context);
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    private void enqueueOk() throws Exception {
        String inner = "{\"error_code\":0,\"data\":{\"status\":0}}";
        String enc = AesCbc.encodeAES(SECRET_KEY, inner);
        String envelope = "{\"error_code\":0,\"data\":\"" + enc + "\"}";
        server.enqueue(new MockResponse().setBody(envelope)
                .setHeader("Content-Type", "application/json"));
    }

    // ── ReactionService ──────────────────────────────────────────────────

    @Test
    @DisplayName("addReaction posts to /api/message/reaction for user")
    void addReaction() throws Exception {
        enqueueOk();
        ReactionService svc = new ReactionService(context, http, handler);
        svc.addReaction(ReactionService.Reaction.LIKE, "100", "50", "user1", ThreadType.USER);
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/api/message/reaction"));
    }

    @Test
    @DisplayName("addReaction posts to /api/group/reaction for group")
    void addReactionGroup() throws Exception {
        enqueueOk();
        ReactionService svc = new ReactionService(context, http, handler);
        svc.addReaction(ReactionService.Reaction.HEART, "100", "50", "group1", ThreadType.GROUP);
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/group/reaction"));
    }

    // ── UserService ──────────────────────────────────────────────────────

    @Test
    @DisplayName("findUser GETs /api/friend/profile/get")
    void findUser() throws Exception {
        enqueueOk();
        UserService svc = new UserService(context, http, handler);
        svc.findUser("0912345678");
        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().contains("/api/friend/profile/get"));
        assertTrue(req.getPath().contains("params="));
    }

    @Test
    @DisplayName("getAllFriends GETs /api/social/friend/getfriends")
    void getAllFriends() throws Exception {
        enqueueOk();
        UserService svc = new UserService(context, http, handler);
        svc.getAllFriends();
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/social/friend/getfriends"));
    }

    @Test
    @DisplayName("blockUser POSTs to /api/friend/block")
    void blockUser() throws Exception {
        enqueueOk();
        UserService svc = new UserService(context, http, handler);
        svc.blockUser("uid-123");
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/api/friend/block"));
    }

    @Test
    @DisplayName("unblockUser POSTs to /api/friend/unblock")
    void unblockUser() throws Exception {
        enqueueOk();
        UserService svc = new UserService(context, http, handler);
        svc.unblockUser("uid-123");
        assertTrue(server.takeRequest().getPath().contains("/api/friend/unblock"));
    }

    @Test
    @DisplayName("changeFriendAlias GETs /api/alias/update")
    void changeFriendAlias() throws Exception {
        enqueueOk();
        UserService svc = new UserService(context, http, handler);
        svc.changeFriendAlias("uid-1", "Nickname");
        assertTrue(server.takeRequest().getPath().contains("/api/alias/update"));
    }

    @Test
    @DisplayName("getAliasList GETs /api/alias/list")
    void getAliasList() throws Exception {
        enqueueOk();
        UserService svc = new UserService(context, http, handler);
        svc.getAliasList();
        assertTrue(server.takeRequest().getPath().contains("/api/alias/list"));
    }

    // ── GroupService ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getGroupInfo POSTs to /api/group/getmg-v2")
    void getGroupInfo() throws Exception {
        enqueueOk();
        GroupService svc = new GroupService(context, http, handler);
        svc.getGroupInfo("g1", "g2");
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/api/group/getmg-v2"));
    }

    @Test
    @DisplayName("getAllGroups GETs /api/group/getlg/v4")
    void getAllGroups() throws Exception {
        enqueueOk();
        GroupService svc = new GroupService(context, http, handler);
        svc.getAllGroups();
        assertTrue(server.takeRequest().getPath().contains("/api/group/getlg/v4"));
    }

    @Test
    @DisplayName("createGroup POSTs to /api/group/create/v2")
    void createGroup() throws Exception {
        enqueueOk();
        GroupService svc = new GroupService(context, http, handler);
        svc.createGroup("Test Group", Arrays.asList("uid1", "uid2"));
        assertTrue(server.takeRequest().getPath().contains("/api/group/create/v2"));
    }

    @Test
    @DisplayName("addUser POSTs to /api/group/invite/v2")
    void addUser() throws Exception {
        enqueueOk();
        GroupService svc = new GroupService(context, http, handler);
        svc.addUser("g1", "uid1");
        assertTrue(server.takeRequest().getPath().contains("/api/group/invite/v2"));
    }

    @Test
    @DisplayName("removeUser POSTs to /api/group/kickout")
    void removeUser() throws Exception {
        enqueueOk();
        GroupService svc = new GroupService(context, http, handler);
        svc.removeUser("g1", "uid1");
        assertTrue(server.takeRequest().getPath().contains("/api/group/kickout"));
    }

    @Test
    @DisplayName("changeOwner GETs /api/group/change-owner")
    void changeOwner() throws Exception {
        enqueueOk();
        GroupService svc = new GroupService(context, http, handler);
        svc.changeOwner("g1", "uid1");
        assertTrue(server.takeRequest().getPath().contains("/api/group/change-owner"));
    }

    @Test
    @DisplayName("changeName POSTs to /api/group/updateinfo")
    void changeName() throws Exception {
        enqueueOk();
        GroupService svc = new GroupService(context, http, handler);
        svc.changeName("g1", "New Name");
        assertTrue(server.takeRequest().getPath().contains("/api/group/updateinfo"));
    }

    // ── StickerService ───────────────────────────────────────────────────

    @Test
    @DisplayName("search GETs /api/message/sticker/search")
    void searchSticker() throws Exception {
        enqueueOk();
        StickerService svc = new StickerService(context, http, handler);
        svc.search("hello");
        assertTrue(server.takeRequest().getPath().contains("/api/message/sticker/search"));
    }

    // ── PollService ──────────────────────────────────────────────────────

    @Test
    @DisplayName("createPoll POSTs to /api/poll/create")
    void createPoll() throws Exception {
        enqueueOk();
        PollService svc = new PollService(context, http, handler);
        svc.createPoll("g1", "Favorite?", Arrays.asList("A", "B", "C"));
        assertTrue(server.takeRequest().getPath().contains("/api/poll/create"));
    }

    @Test
    @DisplayName("getPollDetail POSTs to /api/poll/detail")
    void getPollDetail() throws Exception {
        enqueueOk();
        PollService svc = new PollService(context, http, handler);
        svc.getPollDetail(12345);
        assertTrue(server.takeRequest().getPath().contains("/api/poll/detail"));
    }

    // ── ProfileService ───────────────────────────────────────────────────

    @Test
    @DisplayName("fetchAccountInfo GETs /api/social/profile/me-v2")
    void fetchAccountInfo() throws Exception {
        enqueueOk();
        ProfileService svc = new ProfileService(context, http, handler);
        svc.fetchAccountInfo();
        assertTrue(server.takeRequest().getPath().contains("/api/social/profile/me-v2"));
    }

    // ── SettingsService ──────────────────────────────────────────────────

    @Test
    @DisplayName("getMute GETs /api/social/profile/getmute")
    void getMute() throws Exception {
        enqueueOk();
        SettingsService svc = new SettingsService(context, http, handler);
        svc.getMute();
        assertTrue(server.takeRequest().getPath().contains("/api/social/profile/getmute"));
    }

    @Test
    @DisplayName("getLabels GETs /api/convlabel/get")
    void getLabels() throws Exception {
        enqueueOk();
        SettingsService svc = new SettingsService(context, http, handler);
        svc.getLabels();
        assertTrue(server.takeRequest().getPath().contains("/api/convlabel/get"));
    }
}
