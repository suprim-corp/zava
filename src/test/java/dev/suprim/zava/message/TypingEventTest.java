package dev.suprim.zava.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.conversation.ThreadType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypingEventTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("user typing")
    void userTyping() {
        TypingEvent e = MAPPER.convertValue(MAPPER.createObjectNode()
                .put("uid", "user-1").put("ts", "123").put("isPC", 1), TypingEvent.class);
        assertEquals("user-1", e.getUid());
        assertEquals("123", e.getTs());
        assertTrue(e.isPC());
        assertFalse(e.isGroup());
        assertEquals(ThreadType.USER, e.getType());
        assertEquals("user-1", e.getThreadId());
        assertNotNull(e.toString());
    }

    @Test @DisplayName("group typing")
    void groupTyping() {
        TypingEvent e = MAPPER.convertValue(MAPPER.createObjectNode()
                .put("uid", "user-1").put("ts", "0").put("isPC", 0).put("gid", "group-1"),
                TypingEvent.class);
        assertTrue(e.isGroup());
        assertEquals(ThreadType.GROUP, e.getType());
        assertEquals("group-1", e.getThreadId());
        assertFalse(e.isPC());
    }

    @Test @DisplayName("null gid is not group")
    void nullGid() {
        TypingEvent e = new TypingEvent();
        assertFalse(e.isGroup());
        assertNull(e.getGid());
    }
}
