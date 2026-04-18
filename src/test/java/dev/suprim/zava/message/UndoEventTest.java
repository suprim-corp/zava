package dev.suprim.zava.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UndoEventTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("initialize for user undo")
    void initializeUser() {
        UndoEvent e = MAPPER.convertValue(MAPPER.createObjectNode()
                .put("msgId", "1").put("cliMsgId", "c").put("uidFrom", "sender").put("idTo", "me").put("ts", "0")
                .set("content", MAPPER.createObjectNode().put("globalMsgId", 999).put("deleteMsg", 1)),
                UndoEvent.class);
        e.initialize("me", false);
        assertFalse(e.isSelf());
        assertFalse(e.isGroup());
        assertEquals("sender", e.getThreadId());
        assertEquals(999, e.getGlobalMsgId());
        assertNotNull(e.toString());
    }

    @Test @DisplayName("initialize for self group undo")
    void initializeSelfGroup() {
        UndoEvent e = MAPPER.convertValue(MAPPER.createObjectNode()
                .put("uidFrom", "0").put("idTo", "group-1"),
                UndoEvent.class);
        e.initialize("me", true);
        assertTrue(e.isSelf());
        assertTrue(e.isGroup());
        assertEquals("group-1", e.getThreadId());
        assertEquals("me", e.getUidFrom());
    }

    @Test @DisplayName("getGlobalMsgId returns 0 for null content")
    void getGlobalMsgIdNull() {
        UndoEvent e = new UndoEvent();
        assertEquals(0, e.getGlobalMsgId());
    }

    @Test @DisplayName("all getters")
    void allGetters() {
        UndoEvent e = MAPPER.convertValue(MAPPER.createObjectNode()
                .put("msgId", "m").put("cliMsgId", "c").put("uidFrom", "f")
                .put("idTo", "t").put("ts", "123"),
                UndoEvent.class);
        assertEquals("m", e.getMsgId());
        assertEquals("c", e.getCliMsgId());
        assertEquals("f", e.getUidFrom());
        assertEquals("t", e.getIdTo());
        assertEquals("123", e.getTs());
        assertNull(e.getContent());
    }
}
