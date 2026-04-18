package dev.suprim.zava;

import dev.suprim.zava.auth.CookieLogin;
import dev.suprim.zava.auth.Credentials;
import dev.suprim.zava.auth.QrLogin;
import dev.suprim.zava.internal.session.Context;

import java.util.function.Consumer;

/**
 * Entry point for the Zava SDK.
 *
 * <pre>{@code
 * // Cookie login
 * Zava zava = new Zava();
 * ZavaClient client = zava.login(credentials);
 *
 * // QR login
 * ZavaClient client = zava.loginQR(event -> {
 *     if (event.getType() == QrLogin.QrEventType.QR_GENERATED) {
 *         System.out.println("Scan QR code at qr.png");
 *     }
 * });
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
     * Login using QR code.
     *
     * <p>Generates a QR code, waits for scan + confirm on phone,
     * then automatically logs in with the received cookies.
     *
     * @param callback optional callback for QR events
     * @return a connected {@link ZavaClient}
     * @throws dev.suprim.zava.exception.ZavaAuthException if login fails
     */
    public ZavaClient loginQR(Consumer<QrLogin.QrEvent> callback) {
        return loginQR(null, null, callback);
    }

    /**
     * Login using QR code with custom user agent and language.
     *
     * @param userAgent custom user agent (null for default)
     * @param language  language code (null for "vi")
     * @param callback  optional callback for QR events
     * @return a connected {@link ZavaClient}
     * @throws dev.suprim.zava.exception.ZavaAuthException if login fails
     */
    public ZavaClient loginQR(String userAgent, String language, Consumer<QrLogin.QrEvent> callback) {
        QrLogin qrLogin = new QrLogin();
        QrLogin.QrLoginResult result = qrLogin.login(userAgent, language, callback);

        // Use the QR result to do a cookie login
        Credentials credentials = result.toCredentials();
        return login(credentials);
    }

    /**
     * Get the SDK options.
     */
    public ZavaOptions getOptions() {
        return options;
    }
}
