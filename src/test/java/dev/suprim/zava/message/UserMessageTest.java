package dev.suprim.zava.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("initialize sets threadId from uidFrom when not self")
    void initializeNotSelf() {
        UserMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode()
                        .put("msgId", "100")
                        .put("uidFrom", "sender-1")
                        .put("idTo", "my-uid")
                        .put("ts", "1700000000")
                        .put("status", 1)
                        .put("ttl", 0)
                        .put("cmd", 0)
                        .put("st", 0)
                        .put("at", 0),
                UserMessage.class);

        msg.initialize("my-uid");
        assertFalse(msg.isSelf());
        assertEquals("sender-1", msg.getThreadId());
        assertEquals("sender-1", msg.getUidFrom());
        assertEquals("my-uid", msg.getIdTo());
    }

    @Test @DisplayName("initialize handles self message (uidFrom=0)")
    void initializeSelf() {
        UserMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode()
                        .put("msgId", "100")
                        .put("uidFrom", "0")
                        .put("idTo", "target-1")
                        .put("ts", "1700000000"),
                UserMessage.class);

        msg.initialize("my-uid");
        assertTrue(msg.isSelf());
        assertEquals("target-1", msg.getThreadId());
        assertEquals("my-uid", msg.getUidFrom()); // 0 replaced
    }

    @Test @DisplayName("initialize handles idTo=0")
    void initializeIdToZero() {
        UserMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode()
                        .put("uidFrom", "0")
                        .put("idTo", "0"),
                UserMessage.class);
        msg.initialize("my-uid");
        assertEquals("my-uid", msg.getIdTo());
    }

    @Test @DisplayName("getTextContent returns string content")
    void getTextContentString() {
        UserMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode().put("content", "Hello!"), UserMessage.class);
        assertEquals("Hello!", msg.getTextContent());
    }

    @Test @DisplayName("getTextContent returns JSON for object content")
    void getTextContentObject() {
        ObjectNode node = MAPPER.createObjectNode();
        node.set("content", MAPPER.createObjectNode().put("href", "https://example.com"));
        UserMessage msg = MAPPER.convertValue(node, UserMessage.class);
        assertNotNull(msg.getTextContent());
        assertTrue(msg.getTextContent().contains("example.com"));
    }

    @Test @DisplayName("getTextContent returns null for null content")
    void getTextContentNull() {
        UserMessage msg = new UserMessage();
        assertNull(msg.getTextContent());
    }

    @Test @DisplayName("toString includes key fields")
    void toStringTest() {
        UserMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode()
                        .put("msgId", "99")
                        .put("uidFrom", "sender")
                        .put("idTo", "target")
                        .put("content", "hi"),
                UserMessage.class);
        msg.initialize("me");
        String s = msg.toString();
        assertTrue(s.contains("99"));
        assertTrue(s.contains("sender"));
    }

    @Test @DisplayName("all getters return correct values")
    void allGetters() {
        UserMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode()
                        .put("actionId", "act-1")
                        .put("msgId", "100")
                        .put("cliMsgId", "cli-1")
                        .put("msgType", "1")
                        .put("uidFrom", "from")
                        .put("idTo", "to")
                        .put("dName", "Display")
                        .put("ts", "123")
                        .put("status", 2)
                        .put("notify", "notif")
                        .put("ttl", 60)
                        .put("cmd", 501)
                        .put("st", 1)
                        .put("at", 2)
                        .put("realMsgId", "real-1"),
                UserMessage.class);

        assertEquals("act-1", msg.getActionId());
        assertEquals("100", msg.getMsgId());
        assertEquals("cli-1", msg.getCliMsgId());
        assertEquals("1", msg.getMsgType());
        assertEquals("Display", msg.getDisplayName());
        assertEquals("123", msg.getTs());
        assertEquals(2, msg.getStatus());
        assertEquals("notif", msg.getNotify());
        assertEquals(60, msg.getTtl());
        assertEquals(501, msg.getCmd());
        assertEquals("real-1", msg.getRealMsgId());
        assertNull(msg.getQuote());
    }
}
