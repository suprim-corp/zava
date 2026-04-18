package dev.suprim.zava;

import dev.suprim.zava.auth.CookieLogin;
import dev.suprim.zava.auth.Credentials;
import dev.suprim.zava.internal.session.Context;

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

    /**
     * Login using cookie-based credentials.
     *
     * <p>Flow: parse cookies → getLoginInfo → getServerInfo → return client.
     *
     * @param credentials the login credentials (imei, cookies, userAgent)
     * @return a connected {@link ZavaClient}
     * @throws dev.suprim.zava.exception.ZavaAuthException if login fails
     */
    public ZavaClient login(Credentials credentials) {
        CookieLogin cookieLogin = new CookieLogin(options);
        Context context = cookieLogin.login(credentials);
        return new ZavaClient(context);
    }

    /**
     * Get the SDK options.
     */
    public ZavaOptions getOptions() {
        return options;
    }
}
