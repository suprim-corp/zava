package dev.suprim.zava.internal.ws;

/**
 * WebSocket command types used in the Zalo protocol.
 *
 * <p>Each command is identified by a (cmd, subCmd) pair in the binary frame header.
 */
public enum Command {

    /** Cipher key exchange (receive: server sends AES key). */
    CIPHER_KEY(1, 1),

    /** Ping/keepalive (send). */
    PING(2, 1),

    /** User direct messages (receive). */
    USER_MESSAGE(501, 0),

    /** User seen/delivered receipts (receive). */
    USER_SEEN_DELIVERED(502, 0),

    /** Old user messages (request/response). */
    OLD_USER_MESSAGES(510, 1),

    /** Old group messages (request/response). */
    OLD_GROUP_MESSAGES(511, 1),

    /** Group messages (receive). */
    GROUP_MESSAGE(521, 0),

    /** Group seen/delivered receipts (receive). */
    GROUP_SEEN_DELIVERED(522, 0),

    /** Control events: file_done, group events, friend events (receive). */
    CONTROL(601, 0),

    /** Typing indicators (receive). */
    TYPING(602, 0),

    /** Old user reactions (request/response). */
    OLD_USER_REACTIONS(610, -1), // subCmd 0 or 1

    /** Old group reactions (request/response). */
    OLD_GROUP_REACTIONS(611, -1), // subCmd 0 or 1

    /** Live reactions (receive). */
    REACTION(612, 0),

    /** Duplicate connection detected (receive). */
    DUPLICATE_CONNECTION(3000, 0);

    private final int cmd;
    private final int subCmd;

    Command(int cmd, int subCmd) {
        this.cmd = cmd;
        this.subCmd = subCmd;
    }

    public int getCmd() { return cmd; }
    public int getSubCmd() { return subCmd; }

    /**
     * Find a Command by its cmd value (subCmd may vary).
     *
     * @return the matching Command, or null if not found
     */
    public static Command fromCmd(int cmd) {
        for (Command c : values()) {
            if (c.cmd == cmd) return c;
        }
        return null;
    }

    /**
     * Find a Command by exact (cmd, subCmd) pair.
     *
     * @return the matching Command, or null if not found
     */
    public static Command fromCmdAndSubCmd(int cmd, int subCmd) {
        for (Command c : values()) {
            if (c.cmd == cmd && (c.subCmd == subCmd || c.subCmd == -1)) return c;
        }
        return null;
    }
}
