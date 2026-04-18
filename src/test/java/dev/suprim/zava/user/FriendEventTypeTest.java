package dev.suprim.zava.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class FriendEventTypeTest {

    @ParameterizedTest
    @CsvSource({
            "add, ADD",
            "add_friend, ADD",
            "remove, REMOVE",
            "remove_friend, REMOVE",
            "req_v2, REQUEST",
            "undo_req, UNDO_REQUEST",
            "reject_req, REJECT_REQUEST",
            "seen_fr_req, SEEN_FRIEND_REQUEST",
            "block, BLOCK",
            "unblock, UNBLOCK",
            "block_call, BLOCK_CALL",
            "unblock_call, UNBLOCK_CALL",
            "pin_unpin, PIN_UNPIN",
            "pin_create, PIN_CREATE",
    })
    @DisplayName("fromAct maps all known act strings")
    void fromActKnown(String act, String expected) {
        assertEquals(FriendEventType.valueOf(expected), FriendEventType.fromAct(act));
    }

    @Test @DisplayName("fromAct returns UNKNOWN for null")
    void fromActNull() { assertEquals(FriendEventType.UNKNOWN, FriendEventType.fromAct(null)); }

    @Test @DisplayName("fromAct returns UNKNOWN for unknown act")
    void fromActUnknown() { assertEquals(FriendEventType.UNKNOWN, FriendEventType.fromAct("xyz")); }
}
