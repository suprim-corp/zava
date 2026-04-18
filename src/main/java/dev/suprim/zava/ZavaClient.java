package dev.suprim.zava;

import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.listener.ZavaListener;
import dev.suprim.zava.message.MessageService;

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

    // Lazy-initialized services
    private volatile MessageService messageService;
    private volatile ZavaListener listener;

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
     * Message operations: send, delete, undo, forward.
     */
    public MessageService messages() {
        if (messageService == null) {
            synchronized (this) {
                if (messageService == null) {
                    messageService = new MessageService(context, httpClient, responseHandler);
                }
            }
        }
        return messageService;
    }

    /**
     * Real-time event listener.
     *
     * <pre>{@code
     * client.listener()
     *     .onMessage(msg -> System.out.println(msg))
     *     .start();
     * }</pre>
     */
    public ZavaListener listener() {
        if (listener == null) {
            synchronized (this) {
                if (listener == null) {
                    listener = new ZavaListener(context, httpClient.getOkHttpClient());
                }
            }
        }
        return listener;
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

    // Future domain services:
    // public GroupService groups() { ... }
    // public UserService users() { ... }
    // public ReactionService reactions() { ... }
    // etc.
}
