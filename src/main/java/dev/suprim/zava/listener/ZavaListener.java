package dev.suprim.zava.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.group.GroupEvent;
import dev.suprim.zava.group.GroupEventType;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.ws.Connection;
import dev.suprim.zava.internal.ws.EventDecoder;
import dev.suprim.zava.internal.ws.Frame;
import dev.suprim.zava.message.*;
import dev.suprim.zava.reaction.ReactionEvent;
import dev.suprim.zava.user.FriendEvent;
import dev.suprim.zava.user.FriendEventType;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Real-time event listener for Zalo WebSocket events.
 *
 * <p>Connects to the Zalo WebSocket, decodes incoming events,
 * and dispatches them to registered typed callbacks.
 *
 * <pre>{@code
 * client.listener()
 *     .onUserMessage(msg -> System.out.println(msg.getTextContent()))
 *     .onGroupMessage(msg -> System.out.println(msg.getTextContent()))
 *     .onReaction(react -> System.out.println(react))
 *     .start();
 * }</pre>
 *
 * <p>All callbacks are invoked on the OkHttp WebSocket thread.
 * If callback processing is slow, offload work to your own executor.
 */
public class ZavaListener {

    private static final Logger log = LoggerFactory.getLogger(ZavaListener.class);
    private static final ObjectMapper MAPPER = ResponseHandler.mapper();

    private final Context context;
    private final Connection connection;
    private final String uid;

    // Typed event callbacks
    private Consumer<UserMessage> userMessageCallback;
    private Consumer<GroupMessage> groupMessageCallback;
    private Consumer<ReactionEvent> reactionCallback;
    private Consumer<UndoEvent> undoCallback;
    private Consumer<TypingEvent> typingCallback;
    private Consumer<GroupEvent> groupEventCallback;
    private Consumer<FriendEvent> friendEventCallback;
    private Consumer<SeenEvent> seenCallback;
    private Consumer<DeliveredEvent> deliveredCallback;
    private Consumer<JsonNode> uploadCompleteCallback;

    // Lifecycle callbacks
    private Runnable connectedCallback;
    private BiConsumer<Integer, String> closedCallback;
    private Consumer<Throwable> errorCallback;

    public ZavaListener(Context context, OkHttpClient httpClient) {
        this.context = context;
        this.uid = context.getUid();
        this.connection = new Connection(context, httpClient);
        connection.onFrame(this::dispatchFrame);
    }

    // ── Typed callback registration ──────────────────────────────────────

    public ZavaListener onUserMessage(Consumer<UserMessage> callback) {
        this.userMessageCallback = callback; return this;
    }

    public ZavaListener onGroupMessage(Consumer<GroupMessage> callback) {
        this.groupMessageCallback = callback; return this;
    }

    public ZavaListener onReaction(Consumer<ReactionEvent> callback) {
        this.reactionCallback = callback; return this;
    }

    public ZavaListener onUndo(Consumer<UndoEvent> callback) {
        this.undoCallback = callback; return this;
    }

    public ZavaListener onTyping(Consumer<TypingEvent> callback) {
        this.typingCallback = callback; return this;
    }

    public ZavaListener onGroupEvent(Consumer<GroupEvent> callback) {
        this.groupEventCallback = callback; return this;
    }

    public ZavaListener onFriendEvent(Consumer<FriendEvent> callback) {
        this.friendEventCallback = callback; return this;
    }

    public ZavaListener onSeen(Consumer<SeenEvent> callback) {
        this.seenCallback = callback; return this;
    }

    public ZavaListener onDelivered(Consumer<DeliveredEvent> callback) {
        this.deliveredCallback = callback; return this;
    }

    public ZavaListener onUploadComplete(Consumer<JsonNode> callback) {
        this.uploadCompleteCallback = callback; return this;
    }

    public ZavaListener onConnected(Runnable callback) {
        this.connectedCallback = callback;
        connection.onConnected(callback); return this;
    }

    public ZavaListener onClosed(BiConsumer<Integer, String> callback) {
        this.closedCallback = callback;
        connection.onClosed(callback); return this;
    }

    public ZavaListener onError(Consumer<Throwable> callback) {
        this.errorCallback = callback;
        connection.onError(callback); return this;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    public void start() { start(true); }

    public void start(boolean retryOnClose) { connection.connect(retryOnClose); }

    public void stop() { connection.disconnect(); }

    // ── Frame dispatch ───────────────────────────────────────────────────

    private void dispatchFrame(Frame frame) {
        int cmd = frame.getCmd();
        String cipherKey = connection.getCipherKey();

        try {
            JsonNode parsed = MAPPER.readTree(frame.getPayload());
            JsonNode decoded = EventDecoder.decode(parsed, cipherKey);
            JsonNode data = decoded.has("data") ? decoded.path("data") : decoded;

            switch (cmd) {
                case 501: case 510: dispatchUserMessages(data); break;
                case 521: case 511: dispatchGroupMessages(data); break;
                case 601: dispatchControlEvents(data); break;
                case 602: dispatchTyping(data); break;
                case 612: case 610: case 611: dispatchReactions(data, cmd == 611); break;
                case 502: dispatchSeenDelivered(data, false); break;
                case 522: dispatchSeenDelivered(data, true); break;
                default: log.debug("Unhandled cmd: {}", cmd);
            }
        } catch (Exception e) {
            log.error("Error dispatching frame cmd={}", cmd, e);
            if (errorCallback != null) errorCallback.accept(e);
        }
    }

    private void dispatchUserMessages(JsonNode data) {
        JsonNode msgs = data.path("msgs");
        if (!msgs.isArray()) return;

        for (JsonNode msg : msgs) {
            JsonNode content = msg.path("content");
            if (content.isObject() && content.has("deleteMsg")) {
                if (undoCallback != null) {
                    UndoEvent undo = MAPPER.convertValue(msg, UndoEvent.class);
                    undo.initialize(uid, false);
                    if (!undo.isSelf() || context.getOptions().isSelfListen())
                        undoCallback.accept(undo);
                }
            } else {
                if (userMessageCallback != null) {
                    UserMessage um = MAPPER.convertValue(msg, UserMessage.class);
                    um.initialize(uid);
                    if (!um.isSelf() || context.getOptions().isSelfListen())
                        userMessageCallback.accept(um);
                }
            }
        }
    }

    private void dispatchGroupMessages(JsonNode data) {
        JsonNode msgs = data.path("groupMsgs");
        if (!msgs.isArray()) return;

        for (JsonNode msg : msgs) {
            JsonNode content = msg.path("content");
            if (content.isObject() && content.has("deleteMsg")) {
                if (undoCallback != null) {
                    UndoEvent undo = MAPPER.convertValue(msg, UndoEvent.class);
                    undo.initialize(uid, true);
                    if (!undo.isSelf() || context.getOptions().isSelfListen())
                        undoCallback.accept(undo);
                }
            } else {
                if (groupMessageCallback != null) {
                    GroupMessage gm = MAPPER.convertValue(msg, GroupMessage.class);
                    gm.initialize(uid);
                    if (!gm.isSelf() || context.getOptions().isSelfListen())
                        groupMessageCallback.accept(gm);
                }
            }
        }
    }

    private void dispatchControlEvents(JsonNode data) {
        JsonNode controls = data.path("controls");
        if (!controls.isArray()) return;

        for (JsonNode control : controls) {
            JsonNode content = control.path("content");
            String actType = content.path("act_type").asText("");
            String act = content.path("act").asText("");

            switch (actType) {
                case "file_done":
                    if (uploadCompleteCallback != null) uploadCompleteCallback.accept(content);
                    String fileId = content.path("fileId").asText(null);
                    if (fileId != null) {
                        Context.UploadCallback cb = context.getUploadCallbacks().remove(fileId);
                        if (cb != null) {
                            cb.onComplete(content.path("data").path("url").asText(""), fileId);
                        }
                    }
                    break;

                case "group":
                    if ("join_reject".equals(act)) continue;
                    if (groupEventCallback != null) {
                        JsonNode eventData = content.path("data");
                        if (eventData.isTextual()) {
                            try { eventData = MAPPER.readTree(eventData.asText()); }
                            catch (Exception ignored) {}
                        }
                        String threadId = eventData.has("groupId")
                                ? eventData.path("groupId").asText("")
                                : eventData.path("group_id").asText("");
                        GroupEventType type = GroupEventType.fromAct(act);
                        boolean isSelf = isSelfGroupEvent(eventData, type);
                        groupEventCallback.accept(new GroupEvent(type, act, eventData, threadId, isSelf));
                    }
                    break;

                case "fr":
                    if ("req".equals(act)) continue;
                    if (friendEventCallback != null) {
                        JsonNode eventData = content.path("data");
                        if (eventData.isTextual()) {
                            try { eventData = MAPPER.readTree(eventData.asText()); }
                            catch (Exception ignored) {}
                        }
                        FriendEventType type = FriendEventType.fromAct(act);
                        String threadId = extractFriendThreadId(eventData, type);
                        boolean isSelf = isSelfFriendEvent(eventData, type);
                        friendEventCallback.accept(new FriendEvent(type, eventData, threadId, isSelf));
                    }
                    break;

                default:
                    log.debug("Unhandled control act_type: {}", actType);
            }
        }
    }

    private void dispatchTyping(JsonNode data) {
        JsonNode actions = data.path("actions");
        if (!actions.isArray()) return;

        for (JsonNode action : actions) {
            if ("typing".equals(action.path("act_type").asText(""))) {
                if (typingCallback != null) {
                    try {
                        String rawData = action.path("data").asText("{}");
                        if (!rawData.startsWith("{")) rawData = "{" + rawData + "}";
                        JsonNode typingData = MAPPER.readTree(rawData);
                        TypingEvent te = MAPPER.convertValue(typingData, TypingEvent.class);
                        typingCallback.accept(te);
                    } catch (Exception e) {
                        log.debug("Failed to parse typing event", e);
                    }
                }
            }
        }
    }

    private void dispatchReactions(JsonNode data, boolean isGroup) {
        if (reactionCallback == null) return;

        for (String field : new String[]{"reacts", "reactGroups"}) {
            JsonNode reacts = data.path(field);
            if (!reacts.isArray()) continue;
            boolean group = "reactGroups".equals(field) || isGroup;
            for (JsonNode react : reacts) {
                ReactionEvent re = MAPPER.convertValue(react, ReactionEvent.class);
                re.initialize(uid, group);
                if (!re.isSelf() || context.getOptions().isSelfListen())
                    reactionCallback.accept(re);
            }
        }
    }

    private void dispatchSeenDelivered(JsonNode data, boolean isGroup) {
        JsonNode deliveredMsgs = data.path("delivereds");
        if (deliveredMsgs.isArray() && deliveredMsgs.size() > 0 && deliveredCallback != null) {
            for (JsonNode d : deliveredMsgs) {
                DeliveredEvent de = MAPPER.convertValue(d, DeliveredEvent.class);
                de.initialize(uid, isGroup);
                if (!de.isSelf() || context.getOptions().isSelfListen())
                    deliveredCallback.accept(de);
            }
        }

        String seenField = isGroup ? "groupSeens" : "seens";
        JsonNode seenMsgs = data.path(seenField);
        if (seenMsgs.isArray() && seenMsgs.size() > 0 && seenCallback != null) {
            for (JsonNode s : seenMsgs) {
                SeenEvent se = MAPPER.convertValue(s, SeenEvent.class);
                se.initialize(uid, isGroup);
                if (!se.isSelf() || context.getOptions().isSelfListen())
                    seenCallback.accept(se);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private boolean isSelfGroupEvent(JsonNode data, GroupEventType type) {
        switch (type) {
            case NEW_PIN_TOPIC: case UNPIN_TOPIC: case UPDATE_PIN_TOPIC: case REORDER_PIN_TOPIC:
                return uid.equals(data.path("actorId").asText(""));
            case UPDATE_BOARD: case REMOVE_BOARD:
                return uid.equals(data.path("sourceId").asText(""));
            case ACCEPT_REMIND: case REJECT_REMIND:
                JsonNode members = data.path("updateMembers");
                if (members.isArray()) {
                    for (JsonNode m : members) {
                        if (uid.equals(m.asText(""))) return true;
                    }
                }
                return false;
            case REMIND_TOPIC:
                return uid.equals(data.path("creatorId").asText(""));
            default:
                if (uid.equals(data.path("sourceId").asText(""))) return true;
                JsonNode updateMembers = data.path("updateMembers");
                if (updateMembers.isArray()) {
                    for (JsonNode m : updateMembers) {
                        if (uid.equals(m.path("id").asText(""))) return true;
                    }
                }
                return false;
        }
    }

    private boolean isSelfFriendEvent(JsonNode data, FriendEventType type) {
        switch (type) {
            case ADD: case REMOVE: return false;
            case REQUEST: case UNDO_REQUEST: case REJECT_REQUEST:
                return uid.equals(data.path("fromUid").asText(""));
            case SEEN_FRIEND_REQUEST: return true;
            case PIN_CREATE: case PIN_UNPIN:
                return uid.equals(data.path("actorId").asText(""));
            case BLOCK: case UNBLOCK: case BLOCK_CALL: case UNBLOCK_CALL:
                return true;
            default: return false;
        }
    }

    private String extractFriendThreadId(JsonNode data, FriendEventType type) {
        switch (type) {
            case ADD: case REMOVE: case BLOCK: case UNBLOCK: case BLOCK_CALL: case UNBLOCK_CALL:
                return data.isTextual() ? data.asText("") : data.toString();
            case REQUEST: return data.path("toUid").asText("");
            case UNDO_REQUEST: case REJECT_REQUEST: return data.path("toUid").asText("");
            case SEEN_FRIEND_REQUEST: return uid;
            case PIN_CREATE: case PIN_UNPIN: return data.path("conversationId").asText("");
            default: return "";
        }
    }
}
