package dev.suprim.zava;

import dev.suprim.zava.group.GroupService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.listener.ZavaListener;
import dev.suprim.zava.message.MessageService;
import dev.suprim.zava.poll.PollService;
import dev.suprim.zava.profile.ProfileService;
import dev.suprim.zava.reaction.ReactionService;
import dev.suprim.zava.settings.SettingsService;
import dev.suprim.zava.sticker.StickerService;
import dev.suprim.zava.user.UserService;

/**
 * The main client returned after a successful login.
 *
 * <p>Provides access to all API services and the real-time event listener.
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
    private volatile UserService userService;
    private volatile GroupService groupService;
    private volatile ReactionService reactionService;
    private volatile StickerService stickerService;
    private volatile PollService pollService;
    private volatile ProfileService profileService;
    private volatile SettingsService settingsService;
    private volatile ZavaListener listener;

    ZavaClient(Context context) {
        this.context = context;
        this.httpClient = new HttpClient(context);
        this.responseHandler = new ResponseHandler(context);
    }

    /** Get the logged-in user's UID. */
    public String getUid() { return context.getUid(); }

    /** Message operations: send, delete, undo, forward. */
    public MessageService messages() {
        if (messageService == null) {
            synchronized (this) {
                if (messageService == null)
                    messageService = new MessageService(context, httpClient, responseHandler);
            }
        }
        return messageService;
    }

    /** User/friend operations: find, list, block/unblock, alias. */
    public UserService users() {
        if (userService == null) {
            synchronized (this) {
                if (userService == null)
                    userService = new UserService(context, httpClient, responseHandler);
            }
        }
        return userService;
    }

    /** Group operations: info, create, members, settings. */
    public GroupService groups() {
        if (groupService == null) {
            synchronized (this) {
                if (groupService == null)
                    groupService = new GroupService(context, httpClient, responseHandler);
            }
        }
        return groupService;
    }

    /** Reaction operations: add reactions. */
    public ReactionService reactions() {
        if (reactionService == null) {
            synchronized (this) {
                if (reactionService == null)
                    reactionService = new ReactionService(context, httpClient, responseHandler);
            }
        }
        return reactionService;
    }

    /** Sticker operations: search. */
    public StickerService stickers() {
        if (stickerService == null) {
            synchronized (this) {
                if (stickerService == null)
                    stickerService = new StickerService(context, httpClient, responseHandler);
            }
        }
        return stickerService;
    }

    /** Poll operations: create, detail. */
    public PollService polls() {
        if (pollService == null) {
            synchronized (this) {
                if (pollService == null)
                    pollService = new PollService(context, httpClient, responseHandler);
            }
        }
        return pollService;
    }

    /** Profile operations: account info. */
    public ProfileService profile() {
        if (profileService == null) {
            synchronized (this) {
                if (profileService == null)
                    profileService = new ProfileService(context, httpClient, responseHandler);
            }
        }
        return profileService;
    }

    /** Settings operations: mute, labels. */
    public SettingsService settings() {
        if (settingsService == null) {
            synchronized (this) {
                if (settingsService == null)
                    settingsService = new SettingsService(context, httpClient, responseHandler);
            }
        }
        return settingsService;
    }

    /** Real-time event listener. */
    public ZavaListener listener() {
        if (listener == null) {
            synchronized (this) {
                if (listener == null)
                    listener = new ZavaListener(context, httpClient.getOkHttpClient());
            }
        }
        return listener;
    }

    /** Get the session context (internal). */
    public Context getContext() { return context; }

    /** Get the HTTP client (internal). */
    public HttpClient getHttpClient() { return httpClient; }

    /** Get the response handler (internal). */
    public ResponseHandler getResponseHandler() { return responseHandler; }
}
