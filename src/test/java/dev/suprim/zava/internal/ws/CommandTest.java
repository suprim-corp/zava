package dev.suprim.zava.internal.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandTest {

    @Test
    @DisplayName("fromCmd finds known commands")
    void fromCmd() {
        assertEquals(Command.USER_MESSAGE, Command.fromCmd(501));
        assertEquals(Command.GROUP_MESSAGE, Command.fromCmd(521));
        assertEquals(Command.CONTROL, Command.fromCmd(601));
        assertEquals(Command.TYPING, Command.fromCmd(602));
        assertEquals(Command.REACTION, Command.fromCmd(612));
        assertEquals(Command.DUPLICATE_CONNECTION, Command.fromCmd(3000));
        assertEquals(Command.CIPHER_KEY, Command.fromCmd(1));
        assertEquals(Command.PING, Command.fromCmd(2));
    }

    @Test
    @DisplayName("fromCmd returns null for unknown cmd")
    void fromCmdUnknown() {
        assertNull(Command.fromCmd(9999));
    }

    @Test
    @DisplayName("fromCmdAndSubCmd matches exact pair")
    void fromCmdAndSubCmd() {
        assertEquals(Command.CIPHER_KEY, Command.fromCmdAndSubCmd(1, 1));
        assertEquals(Command.PING, Command.fromCmdAndSubCmd(2, 1));
        assertEquals(Command.USER_MESSAGE, Command.fromCmdAndSubCmd(501, 0));
    }

    @Test
    @DisplayName("fromCmdAndSubCmd returns null for wrong subCmd")
    void fromCmdAndSubCmdWrong() {
        // cmd=1, subCmd=0 should NOT match CIPHER_KEY (which requires subCmd=1)
        assertNull(Command.fromCmdAndSubCmd(1, 0));
    }
}
