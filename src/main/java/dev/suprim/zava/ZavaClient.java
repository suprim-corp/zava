package dev.suprim.zava;

import dev.suprim.zava.board.BoardService;
import dev.suprim.zava.business.BusinessService;
import dev.suprim.zava.group.GroupService;
import dev.suprim.zava.internal.http.FileUploader;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.listener.ZavaListener;
import dev.suprim.zava.message.MessageService;
import dev.suprim.zava.poll.PollService;
import dev.suprim.zava.profile.ProfileService;
import dev.suprim.zava.reaction.ReactionService;
import dev.suprim.zava.reminder.ReminderService;
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

    private volatile MessageService messageService;
    private volatile UserService userService;
    private volatile GroupService groupService;
    private volatile ReactionService reactionService;
    private volatile StickerService stickerService;
    private volatile PollService pollService;
    private volatile ProfileService profileService;
    private volatile SettingsService settingsService;
    private volatile BoardService boardService;
    private volatile ReminderService reminderService;
    private volatile BusinessService businessService;
    private volatile FileUploader fileUploader;
    private volatile ZavaListener listener;

    ZavaClient(Context context) {
        this.context = context;
        this.httpClient = new HttpClient(context);
        this.responseHandler = new ResponseHandler(context);
    }

    public String getUid() { return context.getUid(); }

    /** Messages: send, delete, undo, forward, typing, seen, delivered, sticker. */
    public MessageService messages() {
        if (messageService == null) synchronized (this) {
            if (messageService == null) messageService = new MessageService(context, httpClient, responseHandler);
        }
        return messageService;
    }

    /** Users: find, info, friends, block, alias, friend requests. */
    public UserService users() {
        if (userService == null) synchronized (this) {
            if (userService == null) userService = new UserService(context, httpClient, responseHandler);
        }
        return userService;
    }

    /** Groups: info, create, members, deputies, settings, leave, history. */
    public GroupService groups() {
        if (groupService == null) synchronized (this) {
            if (groupService == null) groupService = new GroupService(context, httpClient, responseHandler);
        }
        return groupService;
    }

    /** Reactions: add reactions. */
    public ReactionService reactions() {
        if (reactionService == null) synchronized (this) {
            if (reactionService == null) reactionService = new ReactionService(context, httpClient, responseHandler);
        }
        return reactionService;
    }

    /** Stickers: search, detail. */
    public StickerService stickers() {
        if (stickerService == null) synchronized (this) {
            if (stickerService == null) stickerService = new StickerService(context, httpClient, responseHandler);
        }
        return stickerService;
    }

    /** Polls: create, detail, add options, lock. */
    public PollService polls() {
        if (pollService == null) synchronized (this) {
            if (pollService == null) pollService = new PollService(context, httpClient, responseHandler);
        }
        return pollService;
    }

    /** Profile: account info, bio update. */
    public ProfileService profile() {
        if (profileService == null) synchronized (this) {
            if (profileService == null) profileService = new ProfileService(context, httpClient, responseHandler);
        }
        return profileService;
    }

    /** Settings: mute, labels, delete chat, pin conversations. */
    public SettingsService settings() {
        if (settingsService == null) synchronized (this) {
            if (settingsService == null) settingsService = new SettingsService(context, httpClient, responseHandler);
        }
        return settingsService;
    }

    /** Board: notes CRUD, list. */
    public BoardService board() {
        if (boardService == null) synchronized (this) {
            if (boardService == null) boardService = new BoardService(context, httpClient, responseHandler);
        }
        return boardService;
    }

    /** Reminders: create, list. */
    public ReminderService reminders() {
        if (reminderService == null) synchronized (this) {
            if (reminderService == null) reminderService = new ReminderService(context, httpClient, responseHandler);
        }
        return reminderService;
    }

    /** Business: auto-reply, quick messages. */
    public BusinessService business() {
        if (businessService == null) synchronized (this) {
            if (businessService == null) businessService = new BusinessService(context, httpClient, responseHandler);
        }
        return businessService;
    }

    /** File upload: images, videos, files (chunked). */
    public FileUploader uploader() {
        if (fileUploader == null) synchronized (this) {
            if (fileUploader == null) fileUploader = new FileUploader(context, httpClient, responseHandler);
        }
        return fileUploader;
    }

    /** Real-time event listener with typed callbacks. */
    public ZavaListener listener() {
        if (listener == null) synchronized (this) {
            if (listener == null) listener = new ZavaListener(context, httpClient.getOkHttpClient());
        }
        return listener;
    }

    public Context getContext() { return context; }
    public HttpClient getHttpClient() { return httpClient; }
    public ResponseHandler getResponseHandler() { return responseHandler; }
}
