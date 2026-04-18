package dev.suprim.zava.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeliveredEventTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("user delivered")
    void userDelivered() {
        var node = MAPPER.createObjectNode().put("msgId", "100").put("seen", 0)
                .put("realMsgId", "r-100").put("mSTs", 1700000000L);
        node.putArray("deliveredUids").add("user-1");
        node.putArray("seenUids");

        DeliveredEvent e = MAPPER.convertValue(node, DeliveredEvent.class);
        e.initialize("me", false);
        assertFalse(e.isGroup());
        assertFalse(e.isSelf());
        assertEquals("user-1", e.getThreadId());
        assertEquals("100", e.getMsgId());
        assertEquals(0, e.getSeen());
        assertEquals("r-100", e.getRealMsgId());
        assertEquals(1700000000L, e.getMSTs());
        assertEquals(List.of("user-1"), e.getDeliveredUids());
        assertTrue(e.getSeenUids().isEmpty());
        assertNull(e.getGroupId());
        assertNotNull(e.toString());
    }

    @Test @DisplayName("group delivered self")
    void groupDeliveredSelf() {
        var node = MAPPER.createObjectNode().put("msgId", "200").put("groupId", "g-1");
        node.putArray("deliveredUids").add("me");

        DeliveredEvent e = MAPPER.convertValue(node, DeliveredEvent.class);
        e.initialize("me", true);
        assertTrue(e.isGroup());
        assertTrue(e.isSelf());
        assertEquals("g-1", e.getThreadId());
    }

    @Test @DisplayName("group delivered not self")
    void groupDeliveredNotSelf() {
        var node = MAPPER.createObjectNode().put("groupId", "g-1");
        node.putArray("deliveredUids").add("other");

        DeliveredEvent e = MAPPER.convertValue(node, DeliveredEvent.class);
        e.initialize("me", true);
        assertFalse(e.isSelf());
    }

    @Test @DisplayName("user threadId null when no deliveredUids")
    void noDeliveredUids() {
        DeliveredEvent e = new DeliveredEvent();
        e.initialize("me", false);
        assertNull(e.getThreadId());
    }
}
