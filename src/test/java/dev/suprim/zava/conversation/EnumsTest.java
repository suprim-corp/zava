package dev.suprim.zava.conversation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumsTest {

    @Test @DisplayName("Gender values")
    void gender() {
        assertEquals(0, Gender.MALE.getValue());
        assertEquals(1, Gender.FEMALE.getValue());
        assertEquals(Gender.MALE, Gender.fromValue(0));
        assertEquals(Gender.FEMALE, Gender.fromValue(1));
        assertThrows(IllegalArgumentException.class, () -> Gender.fromValue(99));
    }

    @Test @DisplayName("AvatarSize values")
    void avatarSize() {
        assertEquals(120, AvatarSize.SMALL.getValue());
        assertEquals(240, AvatarSize.LARGE.getValue());
    }

    @Test @DisplayName("ThreadType values")
    void threadType() {
        assertEquals(0, ThreadType.USER.ordinal());
        assertEquals(1, ThreadType.GROUP.ordinal());
    }
}
