package dev.suprim.zava.reaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReactionEventTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("user reaction not self")
    void userReactionNotSelf() {
        ReactionEvent e = MAPPER.convertValue(MAPPER.createObjectNode()
                .put("actionId", "a").put("msgId", "1").put("cliMsgId", "c")
                .put("msgType", "t").put("uidFrom", "sender").put("idTo", "me")
                .put("dName", "Name").put("ts", "0").put("ttl", 0),
                ReactionEvent.class);
        e.initialize("me", false);
        assertFalse(e.isSelf());
        assertFalse(e.isGroup());
        assertEquals("sender", e.getThreadId());
        assertEquals("a", e.getActionId());
        assertEquals("1", e.getMsgId());
        assertEquals("c", e.getCliMsgId());
        assertEquals("t", e.getMsgType());
        assertEquals("sender", e.getUidFrom());
        assertEquals("me", e.getIdTo());
        assertEquals("Name", e.getDisplayName());
        assertEquals("0", e.getTs());
        assertEquals(0, e.getTtl());
        assertNull(e.getContent());
        assertNotNull(e.toString());
    }

    @Test @DisplayName("group reaction self (uidFrom=0)")
    void groupReactionSelf() {
        ReactionEvent e = MAPPER.convertValue(MAPPER.createObjectNode()
                .put("uidFrom", "0").put("idTo", "group-1"), ReactionEvent.class);
        e.initialize("me", true);
        assertTrue(e.isSelf());
        assertTrue(e.isGroup());
        assertEquals("group-1", e.getThreadId());
        assertEquals("me", e.getUidFrom());
    }

    @Test @DisplayName("idTo=0 gets replaced")
    void idToZero() {
        ReactionEvent e = MAPPER.convertValue(MAPPER.createObjectNode()
                .put("uidFrom", "0").put("idTo", "0"), ReactionEvent.class);
        e.initialize("me", false);
        assertEquals("me", e.getIdTo());
    }
}
