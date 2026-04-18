package dev.suprim.zava;

/**
 * Entry point for the Zava SDK.
 *
 * <pre>{@code
 * Zava zava = new Zava();
 * ZavaClient client = zava.login(credentials);
 *
 * client.messages().send("Hello!", threadId, ThreadType.USER);
 * }</pre>
 */
public final class Zava {

    private final ZavaOptions options;

    public Zava() {
        this(ZavaOptions.defaults());
    }

    public Zava(ZavaOptions options) {
        this.options = options;
    }
}
