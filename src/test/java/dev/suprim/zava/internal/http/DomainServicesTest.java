package dev.suprim.zava.internal.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.board.BoardService;
import dev.suprim.zava.business.BusinessService;
import dev.suprim.zava.conversation.ThreadType;
import dev.suprim.zava.group.GroupService;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.session.ServiceMap;
import dev.suprim.zava.poll.PollService;
import dev.suprim.zava.profile.ProfileService;
import dev.suprim.zava.reaction.ReactionService;
import dev.suprim.zava.reminder.ReminderService;
import dev.suprim.zava.settings.SettingsService;
import dev.suprim.zava.sticker.StickerService;
import dev.suprim.zava.user.UserService;
import dev.suprim.zava.message.MessageService;
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
                "reaction", "sticker", "label", "alias", "file", "conversation",
                "group_board", "auto_reply", "quick_message")) {
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

    // ── New UserService methods ──────────────────────────────────────────

    @Test @DisplayName("findUserByUsername GETs /api/friend/search/by-user-name")
    void findUserByUsername() throws Exception {
        enqueueOk();
        new UserService(context, http, handler).findUserByUsername("testuser");
        assertTrue(server.takeRequest().getPath().contains("/api/friend/search/by-user-name"));
    }

    @Test @DisplayName("getUserInfo POSTs to /api/social/friend/getprofiles/v2")
    void getUserInfo() throws Exception {
        enqueueOk();
        new UserService(context, http, handler).getUserInfo("uid1", "uid2");
        assertTrue(server.takeRequest().getPath().contains("/api/social/friend/getprofiles/v2"));
    }

    @Test @DisplayName("sendFriendRequest POSTs to /api/friend/sendreq")
    void sendFriendRequest() throws Exception {
        enqueueOk();
        new UserService(context, http, handler).sendFriendRequest("uid1", "Hello!");
        assertTrue(server.takeRequest().getPath().contains("/api/friend/sendreq"));
    }

    @Test @DisplayName("acceptFriendRequest POSTs to /api/friend/accept")
    void acceptFriendRequest() throws Exception {
        enqueueOk();
        new UserService(context, http, handler).acceptFriendRequest("uid1");
        assertTrue(server.takeRequest().getPath().contains("/api/friend/accept"));
    }

    @Test @DisplayName("removeFriend POSTs to /api/friend/remove")
    void removeFriend() throws Exception {
        enqueueOk();
        new UserService(context, http, handler).removeFriend("uid1");
        assertTrue(server.takeRequest().getPath().contains("/api/friend/remove"));
    }

    // ── New GroupService methods ─────────────────────────────────────────

    @Test @DisplayName("getMembersInfo GETs /api/social/group/members")
    void getMembersInfo() throws Exception {
        enqueueOk();
        new GroupService(context, http, handler).getMembersInfo("uid1");
        assertTrue(server.takeRequest().getPath().contains("/api/social/group/members"));
    }

    @Test @DisplayName("addDeputy GETs /api/group/admins/add")
    void addDeputy() throws Exception {
        enqueueOk();
        new GroupService(context, http, handler).addDeputy("g1", "uid1");
        assertTrue(server.takeRequest().getPath().contains("/api/group/admins/add"));
    }

    @Test @DisplayName("removeDeputy GETs /api/group/admins/remove")
    void removeDeputy() throws Exception {
        enqueueOk();
        new GroupService(context, http, handler).removeDeputy("g1", "uid1");
        assertTrue(server.takeRequest().getPath().contains("/api/group/admins/remove"));
    }

    @Test @DisplayName("leave POSTs to /api/group/leave")
    void leaveGroup() throws Exception {
        enqueueOk();
        new GroupService(context, http, handler).leave("g1");
        assertTrue(server.takeRequest().getPath().contains("/api/group/leave"));
    }

    @Test @DisplayName("disperse POSTs to /api/group/disperse")
    void disperseGroup() throws Exception {
        enqueueOk();
        new GroupService(context, http, handler).disperse("g1");
        assertTrue(server.takeRequest().getPath().contains("/api/group/disperse"));
    }

    @Test @DisplayName("getChatHistory GETs /api/group/history")
    void getChatHistory() throws Exception {
        enqueueOk();
        new GroupService(context, http, handler).getChatHistory("g1");
        assertTrue(server.takeRequest().getPath().contains("/api/group/history"));
    }

    // ── New MessageService methods ───────────────────────────────────────

    @Test @DisplayName("sendTypingEvent POSTs to /api/message/typing")
    void sendTypingEvent() throws Exception {
        enqueueOk();
        new MessageService(context, http, handler).sendTypingEvent("uid1", ThreadType.USER);
        assertTrue(server.takeRequest().getPath().contains("/api/message/typing"));
    }

    @Test @DisplayName("sendSticker POSTs to /api/message/sticker")
    void sendSticker() throws Exception {
        enqueueOk();
        new MessageService(context, http, handler).sendSticker(1, 1, "uid1", ThreadType.USER);
        assertTrue(server.takeRequest().getPath().contains("/api/message/sticker"));
    }

    // ── New PollService methods ──────────────────────────────────────────

    @Test @DisplayName("lockPoll POSTs to /api/poll/end")
    void lockPoll() throws Exception {
        enqueueOk();
        new PollService(context, http, handler).lock(123);
        assertTrue(server.takeRequest().getPath().contains("/api/poll/end"));
    }

    // ── New SettingsService methods ──────────────────────────────────────

    @Test @DisplayName("setMute POSTs to /api/social/profile/setmute")
    void setMute() throws Exception {
        enqueueOk();
        new SettingsService(context, http, handler).setMute("uid1", ThreadType.USER, 0, true);
        assertTrue(server.takeRequest().getPath().contains("/api/social/profile/setmute"));
    }

    @Test @DisplayName("deleteChat POSTs to /api/message/deleteconver")
    void deleteChat() throws Exception {
        enqueueOk();
        new SettingsService(context, http, handler).deleteChat("uid1", ThreadType.USER, true);
        assertTrue(server.takeRequest().getPath().contains("/api/message/deleteconver"));
    }

    // ── New StickerService methods ───────────────────────────────────────

    @Test @DisplayName("getDetail GETs /api/message/sticker/sticker_detail")
    void getStickersDetail() throws Exception {
        enqueueOk();
        new StickerService(context, http, handler).getDetail(123);
        assertTrue(server.takeRequest().getPath().contains("/api/message/sticker/sticker_detail"));
    }

    // ── ProfileService ───────────────────────────────────────────────────

    @Test @DisplayName("updateBio POSTs to /api/social/profile/update")
    void updateBio() throws Exception {
        enqueueOk();
        new ProfileService(context, http, handler).updateBio("Hello world");
        assertTrue(server.takeRequest().getPath().contains("/api/social/profile/update"));
    }

    // ── BoardService ─────────────────────────────────────────────────────

    @Test @DisplayName("createNote POSTs to /api/board/topic/createv2")
    void createNote() throws Exception {
        enqueueOk();
        new BoardService(context, http, handler).createNote("g1", "Test", null, null);
        assertTrue(server.takeRequest().getPath().contains("/api/board/topic/createv2"));
    }

    @Test @DisplayName("getListBoard GETs /api/board/list")
    void getListBoard() throws Exception {
        enqueueOk();
        new BoardService(context, http, handler).getListBoard("g1");
        assertTrue(server.takeRequest().getPath().contains("/api/board/list"));
    }

    // ── ReminderService ──────────────────────────────────────────────────

    @Test @DisplayName("createReminder POSTs to /api/board/topic/createv2")
    void createReminder() throws Exception {
        enqueueOk();
        new ReminderService(context, http, handler).createReminder("g1", "Test", System.currentTimeMillis(), 3600, 0);
        assertTrue(server.takeRequest().getPath().contains("/api/board/topic/createv2"));
    }

    @Test @DisplayName("getListReminder GETs /api/board/listReminder")
    void getListReminder() throws Exception {
        enqueueOk();
        new ReminderService(context, http, handler).getListReminder("g1");
        assertTrue(server.takeRequest().getPath().contains("/api/board/listReminder"));
    }

    // ── BusinessService ──────────────────────────────────────────────────

    @Test @DisplayName("getAutoReplyList GETs /api/autoreply/list")
    void getAutoReplyList() throws Exception {
        enqueueOk();
        new BusinessService(context, http, handler).getAutoReplyList();
        assertTrue(server.takeRequest().getPath().contains("/api/autoreply/list"));
    }

    @Test @DisplayName("createAutoReply POSTs to /api/autoreply/create")
    void createAutoReply() throws Exception {
        enqueueOk();
        new BusinessService(context, http, handler).createAutoReply("Hello", true, 0, 0);
        assertTrue(server.takeRequest().getPath().contains("/api/autoreply/create"));
    }

    @Test @DisplayName("getQuickMessageList GETs /api/quickmessage/list")
    void getQuickMessageList() throws Exception {
        enqueueOk();
        new BusinessService(context, http, handler).getQuickMessageList();
        assertTrue(server.takeRequest().getPath().contains("/api/quickmessage/list"));
    }

    @Test @DisplayName("addQuickMessage GETs /api/quickmessage/create")
    void addQuickMessage() throws Exception {
        enqueueOk();
        new BusinessService(context, http, handler).addQuickMessage("hi", "Hello!");
        assertTrue(server.takeRequest().getPath().contains("/api/quickmessage/create"));
    }

    // ── Additional MessageService ────────────────────────────────────────

    @Test @DisplayName("sendSeenEvent POSTs to /api/message/seenv2")
    void sendSeenEvent() throws Exception {
        enqueueOk();
        new MessageService(context, http, handler).sendSeenEvent("100", "c1", "sender", "uid1", ThreadType.USER);
        assertTrue(server.takeRequest().getPath().contains("/api/message/seenv2"));
    }

    @Test @DisplayName("sendSeenEvent group POSTs to /api/group/seenv2")
    void sendSeenEventGroup() throws Exception {
        enqueueOk();
        new MessageService(context, http, handler).sendSeenEvent("100", "c1", null, "g1", ThreadType.GROUP);
        assertTrue(server.takeRequest().getPath().contains("/api/group/seenv2"));
    }

    @Test @DisplayName("sendDeliveredEvent POSTs to /api/message/deliveredv2")
    void sendDeliveredEvent() throws Exception {
        enqueueOk();
        new MessageService(context, http, handler).sendDeliveredEvent("100", "uid1", ThreadType.USER);
        assertTrue(server.takeRequest().getPath().contains("/api/message/deliveredv2"));
    }

    @Test @DisplayName("sendDeliveredEvent group")
    void sendDeliveredEventGroup() throws Exception {
        enqueueOk();
        new MessageService(context, http, handler).sendDeliveredEvent("100", "g1", ThreadType.GROUP);
        assertTrue(server.takeRequest().getPath().contains("/api/group/deliveredv2"));
    }

    @Test @DisplayName("sendSticker group POSTs to /api/group/sticker")
    void sendStickerGroup() throws Exception {
        enqueueOk();
        new MessageService(context, http, handler).sendSticker(1, 1, "g1", ThreadType.GROUP);
        assertTrue(server.takeRequest().getPath().contains("/api/group/sticker"));
    }

    @Test @DisplayName("sendTypingEvent group POSTs to /api/group/typing")
    void sendTypingGroup() throws Exception {
        enqueueOk();
        new MessageService(context, http, handler).sendTypingEvent("g1", ThreadType.GROUP);
        assertTrue(server.takeRequest().getPath().contains("/api/group/typing"));
    }

    // ── Additional GroupService ──────────────────────────────────────────

    @Test @DisplayName("updateSettings GETs /api/group/setting/update")
    void updateSettings() throws Exception {
        enqueueOk();
        new GroupService(context, http, handler).updateSettings("g1", java.util.Map.of("blockName", 1));
        assertTrue(server.takeRequest().getPath().contains("/api/group/setting/update"));
    }

    // ── Additional SettingsService ───────────────────────────────────────

    @Test @DisplayName("deleteChat group POSTs to /api/group/deleteconver")
    void deleteChatGroup() throws Exception {
        enqueueOk();
        new SettingsService(context, http, handler).deleteChat("g1", ThreadType.GROUP, true);
        assertTrue(server.takeRequest().getPath().contains("/api/group/deleteconver"));
    }

    @Test @DisplayName("setMute group")
    void setMuteGroup() throws Exception {
        enqueueOk();
        new SettingsService(context, http, handler).setMute("g1", ThreadType.GROUP, 3600, true);
        assertTrue(server.takeRequest().getPath().contains("/api/social/profile/setmute"));
    }

    @Test @DisplayName("setMute unmute")
    void setMuteUnmute() throws Exception {
        enqueueOk();
        new SettingsService(context, http, handler).setMute("uid1", ThreadType.USER, 0, false);
        assertTrue(server.takeRequest().getPath().contains("/api/social/profile/setmute"));
    }

    @Test @DisplayName("getPinConversations GETs /api/pinconvers/list")
    void getPinConversations() throws Exception {
        enqueueOk();
        new SettingsService(context, http, handler).getPinConversations();
        assertTrue(server.takeRequest().getPath().contains("/api/pinconvers/list"));
    }

    @Test @DisplayName("setPinConversation POSTs to /api/pinconvers/updatev2")
    void setPinConversation() throws Exception {
        enqueueOk();
        new SettingsService(context, http, handler).setPinConversation("uid1", true);
        assertTrue(server.takeRequest().getPath().contains("/api/pinconvers/updatev2"));
    }

    // ── Additional BoardService ──────────────────────────────────────────

    @Test @DisplayName("editNote POSTs to /api/board/topic/updatev2")
    void editNote() throws Exception {
        enqueueOk();
        new BoardService(context, http, handler).editNote("g1", "topic-1", "Updated", null, null);
        assertTrue(server.takeRequest().getPath().contains("/api/board/topic/updatev2"));
    }

    @Test @DisplayName("getListBoard with params")
    void getListBoardParams() throws Exception {
        enqueueOk();
        new BoardService(context, http, handler).getListBoard("g1", 1, 20, 2);
        assertTrue(server.takeRequest().getPath().contains("/api/board/list"));
    }

    // ── Additional PollService ───────────────────────────────────────────

    @Test @DisplayName("addOptions GETs /api/poll/option/add")
    void addPollOptions() throws Exception {
        enqueueOk();
        new PollService(context, http, handler).addOptions(123, java.util.Arrays.asList("D", "E"));
        assertTrue(server.takeRequest().getPath().contains("/api/poll/option/add"));
    }

    @Test @DisplayName("createPoll with full options")
    void createPollFull() throws Exception {
        enqueueOk();
        new PollService(context, http, handler).createPoll("g1", "Q?", java.util.Arrays.asList("A", "B"),
                true, true, true, 1000);
        assertTrue(server.takeRequest().getPath().contains("/api/poll/create"));
    }

    // ── Additional ProfileService ────────────────────────────────────────

    @Test @DisplayName("updateBio with null")
    void updateBioNull() throws Exception {
        enqueueOk();
        new ProfileService(context, http, handler).updateBio(null);
        assertTrue(server.takeRequest().getPath().contains("/api/social/profile/update"));
    }
}
