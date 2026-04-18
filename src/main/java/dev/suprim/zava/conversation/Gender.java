package dev.suprim.zava.conversation;

/**
 * Gender values.
 */
public enum Gender {

    MALE(0),
    FEMALE(1);

    private final int value;

    Gender(int value) { this.value = value; }

    public int getValue() { return value; }

    public static Gender fromValue(int value) {
        for (Gender g : values()) {
            if (g.value == value) return g;
        }
        throw new IllegalArgumentException("Unknown Gender value: " + value);
    }
}
