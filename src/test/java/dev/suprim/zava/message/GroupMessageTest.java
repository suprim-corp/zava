package dev.suprim.zava.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.conversation.ThreadType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroupMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("initialize sets threadId from idTo for group")
    void initializeGroup() {
        GroupMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode()
                        .put("uidFrom", "sender")
                        .put("idTo", "group-1"),
                GroupMessage.class);

        msg.initialize("my-uid");
        assertFalse(msg.isSelf());
        assertEquals("group-1", msg.getThreadId());
        assertEquals(ThreadType.GROUP, msg.getType());
    }

    @Test @DisplayName("initialize handles self (uidFrom=0)")
    void initializeSelf() {
        GroupMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode()
                        .put("uidFrom", "0")
                        .put("idTo", "group-1"),
                GroupMessage.class);

        msg.initialize("my-uid");
        assertTrue(msg.isSelf());
        assertEquals("my-uid", msg.getUidFrom());
    }

    @Test @DisplayName("getTextContent works")
    void getTextContent() {
        GroupMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode().put("content", "Hello group"), GroupMessage.class);
        assertEquals("Hello group", msg.getTextContent());
    }

    @Test @DisplayName("all getters work")
    void allGetters() {
        GroupMessage msg = MAPPER.convertValue(
                MAPPER.createObjectNode()
                        .put("actionId", "a").put("msgId", "1").put("cliMsgId", "c")
                        .put("msgType", "t").put("uidFrom", "f").put("idTo", "t")
                        .put("dName", "d").put("ts", "0").put("status", 0)
                        .put("notify", "n").put("ttl", 0).put("cmd", 0).put("st", 0)
                        .put("at", 0).put("realMsgId", "r"),
                GroupMessage.class);

        assertEquals("a", msg.getActionId());
        assertEquals("1", msg.getMsgId());
        assertEquals("c", msg.getCliMsgId());
        assertEquals("t", msg.getMsgType());
        assertEquals("d", msg.getDisplayName());
        assertEquals("0", msg.getTs());
        assertEquals(0, msg.getStatus());
        assertEquals("n", msg.getNotify());
        assertEquals(0, msg.getTtl());
        assertEquals(0, msg.getCmd());
        assertEquals("r", msg.getRealMsgId());
        assertNull(msg.getQuote());
        assertNull(msg.getMentions());
        assertNotNull(msg.toString());
    }
}
