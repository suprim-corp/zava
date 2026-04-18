package dev.suprim.zava.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FriendEventTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("FriendEvent stores all fields correctly")
    void allFields() {
        var data = MAPPER.createObjectNode().put("fromUid", "uid-1");
        FriendEvent e = new FriendEvent(FriendEventType.REQUEST, data, "uid-1", true);
        assertEquals(FriendEventType.REQUEST, e.getType());
        assertEquals("uid-1", e.getThreadId());
        assertTrue(e.isSelf());
        assertNotNull(e.getData());
        assertNotNull(e.toString());
    }
}
