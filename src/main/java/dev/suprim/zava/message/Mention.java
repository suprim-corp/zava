package dev.suprim.zava.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An @mention in a group message.
 *
 * <ul>
 *   <li>{@code uid} — the user ID being mentioned</li>
 *   <li>{@code pos} — the character position in the message text where the mention starts</li>
 *   <li>{@code len} — the length of the mention text</li>
 *   <li>{@code type} — 0 = mention user, 1 = mention all</li>
 * </ul>
 */
public class Mention {

    @JsonProperty("uid")
    private final String uid;

    @JsonProperty("pos")
    private final int pos;

    @JsonProperty("len")
    private final int len;

    @JsonProperty("type")
    private final int type;

    public Mention(String uid, int pos, int len) {
        this(uid, pos, len, 0);
    }

    public Mention(String uid, int pos, int len, int type) {
        this.uid = uid;
        this.pos = pos;
        this.len = len;
        this.type = type;
    }

    /**
     * Create a "mention all" entry.
     */
    public static Mention all(int pos, int len) {
        return new Mention("-1", pos, len, 1);
    }

    public String getUid() { return uid; }
    public int getPos() { return pos; }
    public int getLen() { return len; }
    public int getType() { return type; }
}
