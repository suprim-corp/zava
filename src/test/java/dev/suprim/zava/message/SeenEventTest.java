package dev.suprim.zava.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeenEventTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("user seen")
    void userSeen() {
        SeenEvent e = MAPPER.convertValue(MAPPER.createObjectNode()
                .put("idTo", "user-1").put("msgId", "100").put("realMsgId", "r-100"),
                SeenEvent.class);
        e.initialize("me", false);
        assertFalse(e.isGroup());
        assertFalse(e.isSelf());
        assertEquals("user-1", e.getThreadId());
        assertEquals("100", e.getMsgId());
        assertEquals("r-100", e.getRealMsgId());
        assertNotNull(e.toString());
    }

    @Test @DisplayName("group seen self")
    void groupSeenSelf() {
        var node = MAPPER.createObjectNode()
                .put("groupId", "g-1").put("msgId", "200");
        ArrayNode seenUids = node.putArray("seenUids");
        seenUids.add("me");
        seenUids.add("other");

        SeenEvent e = MAPPER.convertValue(node, SeenEvent.class);
        e.initialize("me", true);
        assertTrue(e.isGroup());
        assertTrue(e.isSelf());
        assertEquals("g-1", e.getThreadId());
        assertEquals("g-1", e.getGroupId());
        assertEquals(List.of("me", "other"), e.getSeenUids());
    }

    @Test @DisplayName("group seen not self")
    void groupSeenNotSelf() {
        var node = MAPPER.createObjectNode().put("groupId", "g-1");
        node.putArray("seenUids").add("other");
        SeenEvent e = MAPPER.convertValue(node, SeenEvent.class);
        e.initialize("me", true);
        assertFalse(e.isSelf());
    }
}
