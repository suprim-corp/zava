package dev.suprim.zava;

import dev.suprim.zava.auth.Credentials;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

/**
 * The main client returned after a successful login.
 *
 * <p>Provides access to all API services (messages, groups, users, etc.)
 * and the real-time event listener.
 *
 * <pre>{@code
 * ZavaClient client = zava.login(credentials);
 * client.messages().send("Hello!", threadId, ThreadType.USER);
 * }</pre>
 */
public class ZavaClient {

    private final Context context;
    private final HttpClient httpClient;
    private final ResponseHandler responseHandler;

    ZavaClient(Context context) {
        this.context = context;
        this.httpClient = new HttpClient(context);
        this.responseHandler = new ResponseHandler(context);
    }

    /**
     * Get the logged-in user's UID.
     */
    public String getUid() {
        return context.getUid();
    }

    /**
     * Get the session context (internal, for service classes).
     */
    public Context getContext() {
        return context;
    }

    /**
     * Get the HTTP client (internal, for service classes).
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Get the response handler (internal, for service classes).
     */
    public ResponseHandler getResponseHandler() {
        return responseHandler;
    }

    // Domain services will be added here as they're implemented:
    // public MessageService messages() { ... }
    // public GroupService groups() { ... }
    // public UserService users() { ... }
    // public ReactionService reactions() { ... }
    // public ZavaListener listener() { ... }
    // etc.
}
