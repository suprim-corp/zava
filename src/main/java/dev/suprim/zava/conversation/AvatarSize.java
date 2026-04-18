package dev.suprim.zava.conversation;

/**
 * Avatar size in pixels for API requests.
 */
public enum AvatarSize {

    SMALL(120),
    LARGE(240);

    private final int value;

    AvatarSize(int value) { this.value = value; }

    public int getValue() { return value; }
}
