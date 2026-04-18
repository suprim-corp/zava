package dev.suprim.zava.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroupEventTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("GroupEvent stores all fields correctly")
    void allFields() {
        var data = MAPPER.createObjectNode().put("groupId", "g-1");
        GroupEvent e = new GroupEvent(GroupEventType.JOIN, "join", data, "g-1", false);
        assertEquals(GroupEventType.JOIN, e.getType());
        assertEquals("join", e.getAct());
        assertEquals("g-1", e.getThreadId());
        assertFalse(e.isSelf());
        assertNotNull(e.getData());
        assertNotNull(e.toString());
    }
}
