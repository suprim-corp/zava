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

    @BeforeEach
    void setUp() {
        Context ctx = Context.builder()
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

        listener = new ZavaListener(ctx, new OkHttpClient());
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
}
