package dev.suprim.zava.conversation;

/**
 * Identifies the type of a message thread.
 */
public enum ThreadType {

    USER(0),
    GROUP(1);

    private final int value;

    ThreadType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ThreadType fromValue(int value) {
        for (ThreadType type : values()) {
            if (type.value == value) return type;
        }
        throw new IllegalArgumentException("Unknown ThreadType value: " + value);
    }
}
