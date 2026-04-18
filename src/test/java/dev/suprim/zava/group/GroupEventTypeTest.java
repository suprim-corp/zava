package dev.suprim.zava.group;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class GroupEventTypeTest {

    @ParameterizedTest
    @CsvSource({
            "join_request, JOIN_REQUEST",
            "join, JOIN",
            "leave, LEAVE",
            "remove_member, REMOVE_MEMBER",
            "block_member, BLOCK_MEMBER",
            "update_setting, UPDATE_SETTING",
            "update, UPDATE",
            "new_link, NEW_LINK",
            "add_admin, ADD_ADMIN",
            "remove_admin, REMOVE_ADMIN",
            "new_pin_topic, NEW_PIN_TOPIC",
            "update_pin_topic, UPDATE_PIN_TOPIC",
            "reorder_pin_topic, REORDER_PIN_TOPIC",
            "update_board, UPDATE_BOARD",
            "remove_board, REMOVE_BOARD",
            "update_topic, UPDATE_TOPIC",
            "unpin_topic, UNPIN_TOPIC",
            "remove_topic, REMOVE_TOPIC",
            "accept_remind, ACCEPT_REMIND",
            "reject_remind, REJECT_REMIND",
            "remind_topic, REMIND_TOPIC",
            "update_avatar, UPDATE_AVATAR",
    })
    @DisplayName("fromAct maps all known act strings")
    void fromActKnown(String act, String expected) {
        assertEquals(GroupEventType.valueOf(expected), GroupEventType.fromAct(act));
    }

    @Test @DisplayName("fromAct returns UNKNOWN for null")
    void fromActNull() { assertEquals(GroupEventType.UNKNOWN, GroupEventType.fromAct(null)); }

    @Test @DisplayName("fromAct returns UNKNOWN for unknown act")
    void fromActUnknown() { assertEquals(GroupEventType.UNKNOWN, GroupEventType.fromAct("totally_unknown")); }

    @Test @DisplayName("getValue returns correct string")
    void getValue() { assertEquals("join", GroupEventType.JOIN.getValue()); }
}
