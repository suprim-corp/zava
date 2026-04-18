package dev.suprim.zava.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.group.GroupEvent;
import dev.suprim.zava.group.GroupEventType;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.http.ZavaCookieJar;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.session.ServiceMap;
import dev.suprim.zava.internal.ws.Frame;
import dev.suprim.zava.message.*;
import dev.suprim.zava.reaction.ReactionEvent;
import dev.suprim.zava.user.FriendEvent;
import dev.suprim.zava.user.FriendEventType;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ZavaListener dispatch logic by directly invoking dispatchFrame via reflection.
 * This avoids the need for a real WebSocket connection.
 */
class ZavaListenerTest {

    private static final ObjectMapper MAPPER = ResponseHandler.mapper();

    private ZavaListener listener;
    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.builder()
                .uid("my-uid")
                .imei("imei")
                .secretKey(Base64.getEncoder().encodeToString("0123456789abcdef".getBytes()))
                .userAgent("UA")
                .language("vi")
                .options(ZavaOptions.defaults())
                .cookieJar(new ZavaCookieJar())
                .serviceMap(new ServiceMap())
                .wsUrls(List.of("wss://fake/ws"))
                .build();

        listener = new ZavaListener(context, new OkHttpClient());
    }

    /** Invoke private dispatchFrame via reflection. */
    private void dispatchFrame(int cmd, int subCmd, String jsonPayload) throws Exception {
        // Build payload wrapper: {data: jsonPayload, encrypt: 0}
        ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.put("data", jsonPayload);
        wrapper.put("encrypt", 0);

        Frame frame = new Frame(1, cmd, subCmd, wrapper.toString());

        Method method = ZavaListener.class.getDeclaredMethod("dispatchFrame", Frame.class);
        method.setAccessible(true);
        method.invoke(listener, frame);
    }

    // Also need to set cipherKey on the connection to non-null so EventDecoder works
    // But since encrypt=0, cipher key is not needed.

    @Test @DisplayName("dispatches user message (cmd=501)")
    void userMessage() throws Exception {
        List<UserMessage> received = new ArrayList<>();
        listener.onUserMessage(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ArrayNode msgs = data.putArray("msgs");
        msgs.addObject().put("uidFrom", "sender").put("idTo", "my-uid")
                .put("msgId", "100").put("content", "Hello!");

        dispatchFrame(501, 0, data.toString());
        assertEquals(1, received.size());
        assertEquals("Hello!", received.get(0).getTextContent());
    }

    @Test @DisplayName("dispatches group message (cmd=521)")
    void groupMessage() throws Exception {
        List<GroupMessage> received = new ArrayList<>();
        listener.onGroupMessage(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("groupMsgs").addObject()
                .put("uidFrom", "sender").put("idTo", "group-1")
                .put("content", "Group msg");

        dispatchFrame(521, 0, data.toString());
        assertEquals(1, received.size());
        assertEquals("Group msg", received.get(0).getTextContent());
    }

    @Test @DisplayName("dispatches undo from user message with deleteMsg")
    void undoEvent() throws Exception {
        List<UndoEvent> received = new ArrayList<>();
        listener.onUndo(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode msg = data.putArray("msgs").addObject();
        msg.put("uidFrom", "sender").put("idTo", "my-uid");
        msg.putObject("content").put("deleteMsg", 1).put("globalMsgId", 999);

        dispatchFrame(501, 0, data.toString());
        assertEquals(1, received.size());
        assertEquals(999, received.get(0).getGlobalMsgId());
    }

    @Test @DisplayName("filters self messages when selfListen=false")
    void filtersSelfMessage() throws Exception {
        List<UserMessage> received = new ArrayList<>();
        listener.onUserMessage(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("msgs").addObject()
                .put("uidFrom", "0").put("idTo", "target") // self
                .put("content", "my msg");

        dispatchFrame(501, 0, data.toString());
        assertEquals(0, received.size()); // filtered out
    }

    @Test @DisplayName("dispatches typing event (cmd=602)")
    void typingEvent() throws Exception {
        List<TypingEvent> received = new ArrayList<>();
        listener.onTyping(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("actions").addObject()
                .put("act_type", "typing").put("act", "typing")
                .put("data", "\"uid\":\"user-1\",\"ts\":\"0\",\"isPC\":0");

        dispatchFrame(602, 0, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("dispatches reaction (cmd=612)")
    void reactionEvent() throws Exception {
        List<ReactionEvent> received = new ArrayList<>();
        listener.onReaction(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("reacts").addObject()
                .put("uidFrom", "reactor").put("idTo", "my-uid").put("msgId", "400");
        data.putArray("reactGroups").addObject()
                .put("uidFrom", "reactor2").put("idTo", "group-1").put("msgId", "401");

        dispatchFrame(612, 0, data.toString());
        assertEquals(2, received.size());
    }

    @Test @DisplayName("dispatches seen (cmd=502)")
    void seenEvent() throws Exception {
        List<SeenEvent> received = new ArrayList<>();
        listener.onSeen(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("seens").addObject()
                .put("idTo", "user-1").put("msgId", "500");

        dispatchFrame(502, 0, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("dispatches delivered (cmd=502)")
    void deliveredEvent() throws Exception {
        List<DeliveredEvent> received = new ArrayList<>();
        listener.onDelivered(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode d = data.putArray("delivereds").addObject();
        d.put("msgId", "600");
        d.putArray("deliveredUids").add("user-1");
        d.putArray("seenUids");

        dispatchFrame(502, 0, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("dispatches group seen (cmd=522)")
    void groupSeen() throws Exception {
        List<SeenEvent> received = new ArrayList<>();
        listener.onSeen(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode s = data.putArray("groupSeens").addObject();
        s.put("groupId", "g-1").put("msgId", "700");
        s.putArray("seenUids").add("other");

        dispatchFrame(522, 0, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("dispatches group event from control (cmd=601)")
    void groupEvent() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "join");
        content.putObject("data").put("groupId", "g-1").put("sourceId", "joiner");

        dispatchFrame(601, 0, data.toString());
        assertEquals(1, received.size());
        assertEquals(GroupEventType.JOIN, received.get(0).getType());
    }

    @Test @DisplayName("dispatches friend event from control (cmd=601)")
    void friendEvent() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "fr").put("act", "req_v2");
        content.putObject("data").put("fromUid", "requester").put("toUid", "my-uid");

        dispatchFrame(601, 0, data.toString());
        assertEquals(1, received.size());
        assertEquals(FriendEventType.REQUEST, received.get(0).getType());
    }

    @Test @DisplayName("ignores join_reject group event")
    void ignoresJoinReject() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        control.putObject("content").put("act_type", "group").put("act", "join_reject");

        dispatchFrame(601, 0, data.toString());
        assertEquals(0, received.size());
    }

    @Test @DisplayName("ignores req friend event (only req_v2)")
    void ignoresReq() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        control.putObject("content").put("act_type", "fr").put("act", "req");

        dispatchFrame(601, 0, data.toString());
        assertEquals(0, received.size());
    }

    @Test @DisplayName("dispatches file_done upload event")
    void fileDoneEvent() throws Exception {
        List<Object> received = new ArrayList<>();
        listener.onUploadComplete(node -> received.add(node));

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "file_done").put("fileId", "f-123");
        content.putObject("data").put("url", "https://cdn.zalo.me/file.jpg");

        dispatchFrame(601, 0, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("old user messages (cmd=510)")
    void oldUserMessages() throws Exception {
        List<UserMessage> received = new ArrayList<>();
        listener.onUserMessage(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("msgs").addObject()
                .put("uidFrom", "sender").put("idTo", "my-uid").put("content", "old msg");

        dispatchFrame(510, 1, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("old group messages (cmd=511)")
    void oldGroupMessages() throws Exception {
        List<GroupMessage> received = new ArrayList<>();
        listener.onGroupMessage(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("groupMsgs").addObject()
                .put("uidFrom", "sender").put("idTo", "g-1").put("content", "old");

        dispatchFrame(511, 1, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("old reactions (cmd=610, 611)")
    void oldReactions() throws Exception {
        List<ReactionEvent> received = new ArrayList<>();
        listener.onReaction(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("reacts").addObject()
                .put("uidFrom", "r").put("idTo", "my-uid").put("msgId", "1");

        dispatchFrame(610, 0, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("lifecycle callbacks are set")
    void lifecycleCallbacks() {
        listener.onConnected(() -> {});
        listener.onClosed((code, reason) -> {});
        listener.onError(t -> {});
        // Just verify no exceptions
    }

    @Test @DisplayName("start and stop")
    void startStop() {
        listener.stop();
    }

    // ── Additional coverage: isSelf detection, group undo, string data, etc. ──

    @Test @DisplayName("group undo dispatched from groupMsgs")
    void groupUndo() throws Exception {
        List<UndoEvent> received = new ArrayList<>();
        listener.onUndo(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode msg = data.putArray("groupMsgs").addObject();
        msg.put("uidFrom", "sender").put("idTo", "group-1");
        msg.putObject("content").put("deleteMsg", 1).put("globalMsgId", 888);

        dispatchFrame(521, 0, data.toString());
        assertEquals(1, received.size());
        assertTrue(received.get(0).isGroup());
    }

    @Test @DisplayName("group event with string data (not object)")
    void groupEventStringData() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "update_setting");
        // data is a JSON string (not object)
        content.put("data", "{\"groupId\":\"g-1\",\"sourceId\":\"my-uid\"}");

        dispatchFrame(601, 0, data.toString());
        assertEquals(1, received.size());
        assertEquals(GroupEventType.UPDATE_SETTING, received.get(0).getType());
    }

    @Test @DisplayName("friend event with string data")
    void friendEventStringData() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "fr").put("act", "add_friend");
        content.put("data", "\"friend-uid\""); // string data

        dispatchFrame(601, 0, data.toString());
        assertEquals(1, received.size());
        assertEquals(FriendEventType.ADD, received.get(0).getType());
    }

    @Test @DisplayName("isSelf group event: pin topic (actorId = uid)")
    void isSelfPinTopic() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "new_pin_topic");
        content.putObject("data").put("groupId", "g-1").put("actorId", "my-uid");

        dispatchFrame(601, 0, data.toString());
        assertEquals(1, received.size());
        assertTrue(received.get(0).isSelf());
    }

    @Test @DisplayName("isSelf group event: update_board (sourceId = uid)")
    void isSelfUpdateBoard() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "update_board");
        content.putObject("data").put("groupId", "g-1").put("sourceId", "my-uid");

        dispatchFrame(601, 0, data.toString());
        assertTrue(received.get(0).isSelf());
    }

    @Test @DisplayName("isSelf group event: accept_remind (updateMembers contains uid)")
    void isSelfAcceptRemind() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "accept_remind");
        ObjectNode eventData = content.putObject("data");
        eventData.put("groupId", "g-1");
        eventData.putArray("updateMembers").add("my-uid");

        dispatchFrame(601, 0, data.toString());
        assertTrue(received.get(0).isSelf());
    }

    @Test @DisplayName("isSelf group event: remind_topic (creatorId = uid)")
    void isSelfRemindTopic() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "remind_topic");
        content.putObject("data").put("group_id", "g-1").put("creatorId", "my-uid");

        dispatchFrame(601, 0, data.toString());
        assertTrue(received.get(0).isSelf());
    }

    @Test @DisplayName("isSelf friend event: block (always self)")
    void isSelfFriendBlock() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "fr").put("act", "block");
        content.put("data", "\"blocked-uid\"");

        dispatchFrame(601, 0, data.toString());
        assertTrue(received.get(0).isSelf());
    }

    @Test @DisplayName("isSelf friend event: pin_create (actorId = uid)")
    void isSelfFriendPinCreate() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "fr").put("act", "pin_create");
        content.putObject("data").put("conversationId", "conv-1").put("actorId", "my-uid");

        dispatchFrame(601, 0, data.toString());
        assertTrue(received.get(0).isSelf());
        assertEquals("conv-1", received.get(0).getThreadId());
    }

    @Test @DisplayName("isSelf friend event: seen_fr_req (always self)")
    void isSelfSeenFriendRequest() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "fr").put("act", "seen_fr_req");
        content.putArray("data").add("uid-1");

        dispatchFrame(601, 0, data.toString());
        assertTrue(received.get(0).isSelf());
    }

    @Test @DisplayName("group delivered (cmd=522)")
    void groupDelivered() throws Exception {
        List<DeliveredEvent> received = new ArrayList<>();
        listener.onDelivered(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode d = data.putArray("delivereds").addObject();
        d.put("msgId", "700").put("groupId", "g-1");
        d.putArray("deliveredUids").add("other");
        d.putArray("seenUids");

        dispatchFrame(522, 0, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("old group reactions (cmd=611)")
    void oldGroupReactions() throws Exception {
        List<ReactionEvent> received = new ArrayList<>();
        listener.onReaction(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("reacts").addObject()
                .put("uidFrom", "r").put("idTo", "g-1").put("msgId", "1");

        dispatchFrame(611, 0, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("unhandled cmd logged but no error")
    void unhandledCmd() throws Exception {
        // Should not throw
        dispatchFrame(9999, 0, "{}");
    }

    @Test @DisplayName("error callback invoked on bad payload")
    void errorCallback() throws Exception {
        List<Throwable> errors = new ArrayList<>();
        listener.onError(errors::add);

        // Invalid JSON inside wrapper should trigger error
        ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.put("data", "not-valid-json{{{");
        wrapper.put("encrypt", 0);

        Frame frame = new Frame(1, 501, 0, wrapper.toString());
        Method method = ZavaListener.class.getDeclaredMethod("dispatchFrame", Frame.class);
        method.setAccessible(true);
        method.invoke(listener, frame);

        assertEquals(1, errors.size());
    }

    // ── Additional coverage: remaining branches ──────────────────────────

    @Test @DisplayName("unknown control act_type dispatched to default")
    void unknownControlActType() throws Exception {
        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        control.putObject("content").put("act_type", "unknown_type").put("act", "x");
        dispatchFrame(601, 0, data.toString());
        // no callback, no error — just logged
    }

    @Test @DisplayName("isSelf group default: updateMembers with id objects")
    void isSelfGroupDefaultUpdateMembers() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "leave");
        ObjectNode eventData = content.putObject("data");
        eventData.put("groupId", "g-1").put("sourceId", "other");
        eventData.putArray("updateMembers").addObject().put("id", "my-uid");

        dispatchFrame(601, 0, data.toString());
        assertTrue(received.get(0).isSelf());
    }

    @Test @DisplayName("isSelf group default: not self")
    void isSelfGroupDefaultNotSelf() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "leave");
        content.putObject("data").put("groupId", "g-1").put("sourceId", "other");

        dispatchFrame(601, 0, data.toString());
        assertFalse(received.get(0).isSelf());
    }

    @Test @DisplayName("friend event: undo_req")
    void friendUndoReq() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "fr").put("act", "undo_req");
        content.putObject("data").put("fromUid", "my-uid").put("toUid", "target");

        dispatchFrame(601, 0, data.toString());
        assertEquals(FriendEventType.UNDO_REQUEST, received.get(0).getType());
        assertTrue(received.get(0).isSelf());
        assertEquals("target", received.get(0).getThreadId());
    }

    @Test @DisplayName("friend event: remove_friend")
    void friendRemove() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "fr").put("act", "remove_friend");
        content.put("data", "\"removed-uid\"");

        dispatchFrame(601, 0, data.toString());
        assertEquals(FriendEventType.REMOVE, received.get(0).getType());
        assertFalse(received.get(0).isSelf());
    }

    @Test @DisplayName("friend event: unblock")
    void friendUnblock() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "fr").put("act", "unblock");
        content.put("data", "\"uid\"");

        dispatchFrame(601, 0, data.toString());
        assertTrue(received.get(0).isSelf());
    }

    @Test @DisplayName("friend event: unknown type")
    void friendUnknownType() throws Exception {
        List<FriendEvent> received = new ArrayList<>();
        listener.onFriendEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "fr").put("act", "some_unknown");
        content.put("data", "\"x\"");

        dispatchFrame(601, 0, data.toString());
        assertEquals(FriendEventType.UNKNOWN, received.get(0).getType());
        assertFalse(received.get(0).isSelf()); // default returns false
    }

    @Test @DisplayName("group event with group_id field (not groupId)")
    void groupEventGroupIdField() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "update");
        content.putObject("data").put("group_id", "g-2").put("sourceId", "other");

        dispatchFrame(601, 0, data.toString());
        assertEquals("g-2", received.get(0).getThreadId());
    }

    @Test @DisplayName("upload callback triggered from CallbackMap")
    void uploadCallbackFromMap() throws Exception {
        List<String[]> callbacks = new ArrayList<>();
        context.getUploadCallbacks().put("f-999", (url, fid) -> callbacks.add(new String[]{url, fid}));

        List<Object> uploads = new ArrayList<>();
        listener.onUploadComplete(uploads::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "file_done").put("fileId", "f-999");
        content.putObject("data").put("url", "https://cdn.zalo.me/file.jpg");

        dispatchFrame(601, 0, data.toString());
        assertEquals(1, uploads.size());
        assertEquals(1, callbacks.size());
        assertEquals("https://cdn.zalo.me/file.jpg", callbacks.get(0)[0]);
    }

    @Test @DisplayName("typing with data starting with {")
    void typingWithBraces() throws Exception {
        List<TypingEvent> received = new ArrayList<>();
        listener.onTyping(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("actions").addObject()
                .put("act_type", "typing")
                .put("data", "{\"uid\":\"u1\",\"ts\":\"0\",\"isPC\":1}");

        dispatchFrame(602, 0, data.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("reject_remind not self when uid not in updateMembers")
    void rejectRemindNotSelf() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode control = data.putArray("controls").addObject();
        ObjectNode content = control.putObject("content");
        content.put("act_type", "group").put("act", "reject_remind");
        ObjectNode eventData = content.putObject("data");
        eventData.put("groupId", "g-1");
        eventData.putArray("updateMembers").add("other-uid");

        dispatchFrame(601, 0, data.toString());
        assertFalse(received.get(0).isSelf());
    }

    // ── Missing line coverage from Codecov ───────────────────────────────

    @Test @DisplayName("dispatchUserMessages: no msgs field → early return")
    void userMessageNoMsgs() throws Exception {
        List<UserMessage> received = new ArrayList<>();
        listener.onUserMessage(received::add);
        dispatchFrame(501, 0, "{\"other\":1}");
        assertEquals(0, received.size());
    }

    @Test @DisplayName("dispatchGroupMessages: no groupMsgs field → early return")
    void groupMessageNoGroupMsgs() throws Exception {
        List<GroupMessage> received = new ArrayList<>();
        listener.onGroupMessage(received::add);
        dispatchFrame(521, 0, "{\"other\":1}");
        assertEquals(0, received.size());
    }

    @Test @DisplayName("dispatchControlEvents: no controls field → early return")
    void controlNoControls() throws Exception {
        List<GroupEvent> received = new ArrayList<>();
        listener.onGroupEvent(received::add);
        dispatchFrame(601, 0, "{\"other\":1}");
        assertEquals(0, received.size());
    }

    @Test @DisplayName("self undo user message with selfListen disabled → filtered")
    void selfUndoFiltered() throws Exception {
        List<UndoEvent> received = new ArrayList<>();
        listener.onUndo(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode msg = data.putArray("msgs").addObject();
        msg.put("uidFrom", "0").put("idTo", "target");
        msg.putObject("content").put("deleteMsg", 1).put("globalMsgId", 1);

        dispatchFrame(501, 0, data.toString());
        assertEquals(0, received.size()); // selfListen=false, uidFrom=0 → filtered
    }

    @Test @DisplayName("self group undo with selfListen disabled → filtered")
    void selfGroupUndoFiltered() throws Exception {
        List<UndoEvent> received = new ArrayList<>();
        listener.onUndo(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode msg = data.putArray("groupMsgs").addObject();
        msg.put("uidFrom", "0").put("idTo", "g-1");
        msg.putObject("content").put("deleteMsg", 1).put("globalMsgId", 2);

        dispatchFrame(521, 0, data.toString());
        assertEquals(0, received.size());
    }

    @Test @DisplayName("self group message with selfListen disabled → filtered")
    void selfGroupMessageFiltered() throws Exception {
        List<GroupMessage> received = new ArrayList<>();
        listener.onGroupMessage(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("groupMsgs").addObject()
                .put("uidFrom", "0").put("idTo", "g-1").put("content", "self msg");

        dispatchFrame(521, 0, data.toString());
        assertEquals(0, received.size());
    }

    @Test @DisplayName("self reaction with selfListen disabled → filtered")
    void selfReactionFiltered() throws Exception {
        List<ReactionEvent> received = new ArrayList<>();
        listener.onReaction(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("reacts").addObject()
                .put("uidFrom", "0").put("idTo", "target").put("msgId", "1");

        dispatchFrame(612, 0, data.toString());
        assertEquals(0, received.size());
    }

    @Test @DisplayName("self delivered with selfListen disabled → filtered")
    void selfDeliveredFiltered() throws Exception {
        List<DeliveredEvent> received = new ArrayList<>();
        listener.onDelivered(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode d = data.putArray("delivereds").addObject();
        d.put("msgId", "1").put("groupId", "g-1");
        d.putArray("deliveredUids").add("my-uid");
        d.putArray("seenUids");

        dispatchFrame(522, 0, data.toString());
        assertEquals(0, received.size());
    }

    @Test @DisplayName("self seen with selfListen disabled → filtered")
    void selfSeenFiltered() throws Exception {
        List<SeenEvent> received = new ArrayList<>();
        listener.onSeen(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode s = data.putArray("groupSeens").addObject();
        s.put("groupId", "g-1");
        s.putArray("seenUids").add("my-uid");

        dispatchFrame(522, 0, data.toString());
        assertEquals(0, received.size());
    }

    @Test @DisplayName("decoded without data field uses decoded directly")
    void decodedWithoutDataField() throws Exception {
        // Build payload where decoded JSON has no "data" field → ternary line 147
        List<UserMessage> received = new ArrayList<>();
        listener.onUserMessage(received::add);

        // The decoded result is the msgs directly at top level
        ObjectNode topLevel = MAPPER.createObjectNode();
        topLevel.putArray("msgs").addObject()
                .put("uidFrom", "sender").put("idTo", "my-uid").put("content", "hi");

        // Wrap so encrypt=0, data=topLevel (no inner "data" nesting)
        dispatchFrame(501, 0, topLevel.toString());
        assertEquals(1, received.size());
    }

    @Test @DisplayName("no callback registered → dispatch still works without NPE")
    void noCallbacksRegistered() throws Exception {
        // Fresh listener with no callbacks — all dispatch paths hit null checks
        ZavaListener bare = new ZavaListener(context, new OkHttpClient());

        Method method = ZavaListener.class.getDeclaredMethod("dispatchFrame", Frame.class);
        method.setAccessible(true);

        // User message (no userMessageCallback)
        ObjectNode userMsgData = MAPPER.createObjectNode();
        userMsgData.putArray("msgs").addObject()
                .put("uidFrom", "s").put("idTo", "t").put("content", "x");
        method.invoke(bare, makeFrame(501, 0, userMsgData.toString()));

        // User undo (no undoCallback)
        ObjectNode undoData = MAPPER.createObjectNode();
        ObjectNode undoMsg = undoData.putArray("msgs").addObject();
        undoMsg.put("uidFrom", "s").put("idTo", "t");
        undoMsg.putObject("content").put("deleteMsg", 1);
        method.invoke(bare, makeFrame(501, 0, undoData.toString()));

        // Group message (no groupMessageCallback)
        ObjectNode groupMsgData = MAPPER.createObjectNode();
        groupMsgData.putArray("groupMsgs").addObject()
                .put("uidFrom", "s").put("idTo", "g").put("content", "x");
        method.invoke(bare, makeFrame(521, 0, groupMsgData.toString()));

        // Group undo (no undoCallback)
        ObjectNode groupUndoData = MAPPER.createObjectNode();
        ObjectNode groupUndoMsg = groupUndoData.putArray("groupMsgs").addObject();
        groupUndoMsg.put("uidFrom", "s").put("idTo", "g");
        groupUndoMsg.putObject("content").put("deleteMsg", 1);
        method.invoke(bare, makeFrame(521, 0, groupUndoData.toString()));

        // Control file_done (no uploadCompleteCallback, no callback in map)
        ObjectNode fileDoneData = MAPPER.createObjectNode();
        ObjectNode fileDoneCtrl = fileDoneData.putArray("controls").addObject();
        ObjectNode fileDoneContent = fileDoneCtrl.putObject("content");
        fileDoneContent.put("act_type", "file_done").put("fileId", "f-none");
        fileDoneContent.putObject("data").put("url", "x");
        method.invoke(bare, makeFrame(601, 0, fileDoneData.toString()));

        // Control file_done with null fileId
        ObjectNode fileDoneNullId = MAPPER.createObjectNode();
        ObjectNode fileDoneNullCtrl = fileDoneNullId.putArray("controls").addObject();
        fileDoneNullCtrl.putObject("content").put("act_type", "file_done");
        method.invoke(bare, makeFrame(601, 0, fileDoneNullId.toString()));

        // Control group (no groupEventCallback)
        ObjectNode groupEvtData = MAPPER.createObjectNode();
        ObjectNode groupEvtCtrl = groupEvtData.putArray("controls").addObject();
        groupEvtCtrl.putObject("content").put("act_type", "group").put("act", "join");
        method.invoke(bare, makeFrame(601, 0, groupEvtData.toString()));

        // Control fr (no friendEventCallback)
        ObjectNode frData = MAPPER.createObjectNode();
        ObjectNode frCtrl = frData.putArray("controls").addObject();
        frCtrl.putObject("content").put("act_type", "fr").put("act", "add_friend");
        method.invoke(bare, makeFrame(601, 0, frData.toString()));

        // Typing (no typingCallback)
        ObjectNode typingData = MAPPER.createObjectNode();
        typingData.putArray("actions").addObject().put("act_type", "typing").put("data", "{}");
        method.invoke(bare, makeFrame(602, 0, typingData.toString()));

        // Reaction (no reactionCallback)
        ObjectNode reactData = MAPPER.createObjectNode();
        reactData.putArray("reacts").addObject().put("uidFrom", "r").put("idTo", "t");
        method.invoke(bare, makeFrame(612, 0, reactData.toString()));

        // Seen (no seenCallback)
        ObjectNode seenData = MAPPER.createObjectNode();
        seenData.putArray("seens").addObject().put("idTo", "u").put("msgId", "1");
        method.invoke(bare, makeFrame(502, 0, seenData.toString()));

        // Delivered (no deliveredCallback)
        ObjectNode delData = MAPPER.createObjectNode();
        ObjectNode d = delData.putArray("delivereds").addObject();
        d.put("msgId", "1"); d.putArray("deliveredUids").add("u");
        method.invoke(bare, makeFrame(502, 0, delData.toString()));

        // All passed without NPE
    }

    private Frame makeFrame(int cmd, int subCmd, String innerJson) {
        ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.put("data", innerJson);
        wrapper.put("encrypt", 0);
        return new Frame(1, cmd, subCmd, wrapper.toString());
    }

    // ── selfListen=true tests ────────────────────────────────────────────

    @Test @DisplayName("self user message delivered with selfListen=true")
    void selfMessageWithSelfListen() throws Exception {
        Context selfCtx = Context.builder()
                .uid("my-uid").imei("imei")
                .secretKey(Base64.getEncoder().encodeToString("0123456789abcdef".getBytes()))
                .userAgent("UA").language("vi")
                .options(ZavaOptions.builder().selfListen(true).build())
                .cookieJar(new ZavaCookieJar()).serviceMap(new ServiceMap())
                .wsUrls(List.of("wss://fake/ws")).build();
        ZavaListener selfListener = new ZavaListener(selfCtx, new OkHttpClient());

        List<UserMessage> received = new ArrayList<>();
        selfListener.onUserMessage(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("msgs").addObject()
                .put("uidFrom", "0").put("idTo", "target").put("content", "self");

        Method method = ZavaListener.class.getDeclaredMethod("dispatchFrame", Frame.class);
        method.setAccessible(true);
        method.invoke(selfListener, makeFrame(501, 0, data.toString()));

        assertEquals(1, received.size()); // selfListen=true → delivered
    }

    @Test @DisplayName("self undo delivered with selfListen=true")
    void selfUndoWithSelfListen() throws Exception {
        Context selfCtx = Context.builder()
                .uid("my-uid").imei("imei")
                .secretKey(Base64.getEncoder().encodeToString("0123456789abcdef".getBytes()))
                .userAgent("UA").language("vi")
                .options(ZavaOptions.builder().selfListen(true).build())
                .cookieJar(new ZavaCookieJar()).serviceMap(new ServiceMap())
                .wsUrls(List.of("wss://fake/ws")).build();
        ZavaListener selfListener = new ZavaListener(selfCtx, new OkHttpClient());

        List<UndoEvent> received = new ArrayList<>();
        selfListener.onUndo(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode msg = data.putArray("msgs").addObject();
        msg.put("uidFrom", "0").put("idTo", "target");
        msg.putObject("content").put("deleteMsg", 1).put("globalMsgId", 1);

        Method method = ZavaListener.class.getDeclaredMethod("dispatchFrame", Frame.class);
        method.setAccessible(true);
        method.invoke(selfListener, makeFrame(501, 0, data.toString()));

        assertEquals(1, received.size());
    }

    @Test @DisplayName("self group message delivered with selfListen=true")
    void selfGroupMessageWithSelfListen() throws Exception {
        Context selfCtx = Context.builder()
                .uid("my-uid").imei("imei")
                .secretKey(Base64.getEncoder().encodeToString("0123456789abcdef".getBytes()))
                .userAgent("UA").language("vi")
                .options(ZavaOptions.builder().selfListen(true).build())
                .cookieJar(new ZavaCookieJar()).serviceMap(new ServiceMap())
                .wsUrls(List.of("wss://fake/ws")).build();
        ZavaListener selfListener = new ZavaListener(selfCtx, new OkHttpClient());

        List<GroupMessage> received = new ArrayList<>();
        selfListener.onGroupMessage(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("groupMsgs").addObject()
                .put("uidFrom", "0").put("idTo", "g-1").put("content", "self");

        Method method = ZavaListener.class.getDeclaredMethod("dispatchFrame", Frame.class);
        method.setAccessible(true);
        method.invoke(selfListener, makeFrame(521, 0, data.toString()));

        assertEquals(1, received.size());
    }

    @Test @DisplayName("typing non-typing act_type ignored")
    void typingNonTypingIgnored() throws Exception {
        List<TypingEvent> received = new ArrayList<>();
        listener.onTyping(received::add);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("actions").addObject().put("act_type", "not_typing");

        dispatchFrame(602, 0, data.toString());
        assertEquals(0, received.size());
    }

    @Test @DisplayName("typing no actions array → early return")
    void typingNoActions() throws Exception {
        List<TypingEvent> received = new ArrayList<>();
        listener.onTyping(received::add);
        dispatchFrame(602, 0, "{\"other\":1}");
        assertEquals(0, received.size());
    }

    @Test @DisplayName("reaction no reactionCallback → early return")
    void reactionNoCallback() throws Exception {
        // Fresh listener without reaction callback
        ZavaListener bare = new ZavaListener(context, new OkHttpClient());
        Method method = ZavaListener.class.getDeclaredMethod("dispatchFrame", Frame.class);
        method.setAccessible(true);

        ObjectNode data = MAPPER.createObjectNode();
        data.putArray("reacts").addObject().put("uidFrom", "r").put("idTo", "t");
        method.invoke(bare, makeFrame(612, 0, data.toString()));
        // no NPE
    }
}
