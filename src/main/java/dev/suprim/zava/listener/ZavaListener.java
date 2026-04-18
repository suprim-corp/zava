package dev.suprim.zava.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.ws.Command;
import dev.suprim.zava.internal.ws.Connection;
import dev.suprim.zava.internal.ws.EventDecoder;
import dev.suprim.zava.internal.ws.Frame;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Real-time event listener for Zalo WebSocket events.
 *
 * <p>Connects to the Zalo WebSocket, decodes incoming events,
 * and dispatches them to registered callbacks.
 *
 * <pre>{@code
 * client.listener()
 *     .onMessage(event -> System.out.println(event))
 *     .onGroupEvent(event -> System.out.println(event))
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

    // Event callbacks — raw JsonNode for now, typed models in later phases
    private Consumer<JsonNode> messageCallback;
    private Consumer<JsonNode> reactionCallback;
    private Consumer<JsonNode> undoCallback;
    private Consumer<JsonNode> typingCallback;
    private Consumer<JsonNode> groupEventCallback;
    private Consumer<JsonNode> friendEventCallback;
    private Consumer<JsonNode> seenCallback;
    private Consumer<JsonNode> deliveredCallback;
    private Consumer<JsonNode> uploadCompleteCallback;

    // Lifecycle callbacks
    private Runnable connectedCallback;
    private BiConsumer<Integer, String> closedCallback;
    private Consumer<Throwable> errorCallback;

    public ZavaListener(Context context, OkHttpClient httpClient) {
        this.context = context;
        this.connection = new Connection(context, httpClient);

        // Wire up frame dispatch
        connection.onFrame(this::dispatchFrame);
    }

    // ── Builder-style callback registration ──────────────────────────────

    public ZavaListener onMessage(Consumer<JsonNode> callback) {
        this.messageCallback = callback;
        return this;
    }

    public ZavaListener onReaction(Consumer<JsonNode> callback) {
        this.reactionCallback = callback;
        return this;
    }

    public ZavaListener onUndo(Consumer<JsonNode> callback) {
        this.undoCallback = callback;
        return this;
    }

    public ZavaListener onTyping(Consumer<JsonNode> callback) {
        this.typingCallback = callback;
        return this;
    }

    public ZavaListener onGroupEvent(Consumer<JsonNode> callback) {
        this.groupEventCallback = callback;
        return this;
    }

    public ZavaListener onFriendEvent(Consumer<JsonNode> callback) {
        this.friendEventCallback = callback;
        return this;
    }

    public ZavaListener onSeen(Consumer<JsonNode> callback) {
        this.seenCallback = callback;
        return this;
    }

    public ZavaListener onDelivered(Consumer<JsonNode> callback) {
        this.deliveredCallback = callback;
        return this;
    }

    public ZavaListener onUploadComplete(Consumer<JsonNode> callback) {
        this.uploadCompleteCallback = callback;
        return this;
    }

    public ZavaListener onConnected(Runnable callback) {
        this.connectedCallback = callback;
        connection.onConnected(callback);
        return this;
    }

    public ZavaListener onClosed(BiConsumer<Integer, String> callback) {
        this.closedCallback = callback;
        connection.onClosed(callback);
        return this;
    }

    public ZavaListener onError(Consumer<Throwable> callback) {
        this.errorCallback = callback;
        connection.onError(callback);
        return this;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Start listening for events.
     */
    public void start() {
        start(true);
    }

    /**
     * Start listening for events.
     *
     * @param retryOnClose whether to auto-reconnect on close
     */
    public void start(boolean retryOnClose) {
        connection.connect(retryOnClose);
    }

    /**
     * Stop listening and disconnect.
     */
    public void stop() {
        connection.disconnect();
    }

    // ── Frame dispatch ───────────────────────────────────────────────────

    private void dispatchFrame(Frame frame) {
        int cmd = frame.getCmd();
        String cipherKey = connection.getCipherKey();

        try {
            JsonNode parsed = MAPPER.readTree(frame.getPayload());
            JsonNode decoded = EventDecoder.decode(parsed, cipherKey);
            JsonNode data = decoded.has("data") ? decoded.path("data") : decoded;

            switch (cmd) {
                case 501: // User messages
                    dispatchMessages(data, "msgs", false);
                    break;

                case 521: // Group messages
                    dispatchMessages(data, "groupMsgs", true);
                    break;

                case 601: // Control events
                    dispatchControlEvents(data);
                    break;

                case 602: // Typing
                    dispatchTyping(data);
                    break;

                case 612: // Reactions
                    dispatchReactions(data);
                    break;

                case 502: // User seen/delivered
                    dispatchSeenDelivered(data, false);
                    break;

                case 522: // Group seen/delivered
                    dispatchSeenDelivered(data, true);
                    break;

                case 510: // Old user messages
                    dispatchMessages(data, "msgs", false);
                    break;

                case 511: // Old group messages
                    dispatchMessages(data, "groupMsgs", true);
                    break;

                case 610: // Old user reactions
                case 611: // Old group reactions
                    dispatchReactions(data);
                    break;

                default:
                    log.debug("Unhandled cmd: {}", cmd);
            }

        } catch (Exception e) {
            log.error("Error dispatching frame cmd={}", cmd, e);
            if (errorCallback != null) errorCallback.accept(e);
        }
    }

    private void dispatchMessages(JsonNode data, String field, boolean isGroup) {
        JsonNode msgs = data.path(field);
        if (!msgs.isArray()) return;

        for (JsonNode msg : msgs) {
            // Check for undo/delete
            JsonNode content = msg.path("content");
            if (content.isObject() && content.has("deleteMsg")) {
                if (undoCallback != null) undoCallback.accept(msg);
            } else {
                // Check selfListen
                if (!context.getOptions().isSelfListen()) {
                    String uidFrom = msg.path("uidFrom").asText("");
                    if ("0".equals(uidFrom)) continue;
                }
                if (messageCallback != null) messageCallback.accept(msg);
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
                    if (uploadCompleteCallback != null) {
                        uploadCompleteCallback.accept(content);
                    }
                    // Also trigger upload callback map
                    String fileId = content.path("fileId").asText(null);
                    if (fileId != null) {
                        Context.UploadCallback cb = context.getUploadCallbacks().remove(fileId);
                        if (cb != null) {
                            cb.onComplete(
                                    content.path("data").path("url").asText(""),
                                    fileId);
                        }
                    }
                    break;

                case "group":
                    if ("join_reject".equals(act)) continue; // Zalo bug, ignore
                    if (groupEventCallback != null) groupEventCallback.accept(control);
                    break;

                case "fr":
                    if ("req".equals(act)) continue; // Zalo sends both req and req_v2
                    if (friendEventCallback != null) friendEventCallback.accept(control);
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
                if (typingCallback != null) typingCallback.accept(action);
            }
        }
    }

    private void dispatchReactions(JsonNode data) {
        if (reactionCallback == null) return;

        JsonNode reacts = data.path("reacts");
        if (reacts.isArray()) {
            for (JsonNode react : reacts) {
                reactionCallback.accept(react);
            }
        }

        JsonNode reactGroups = data.path("reactGroups");
        if (reactGroups.isArray()) {
            for (JsonNode react : reactGroups) {
                reactionCallback.accept(react);
            }
        }
    }

    private void dispatchSeenDelivered(JsonNode data, boolean isGroup) {
        // Delivered
        JsonNode deliveredMsgs = data.path("delivereds");
        if (deliveredMsgs.isArray() && deliveredMsgs.size() > 0 && deliveredCallback != null) {
            for (JsonNode d : deliveredMsgs) {
                deliveredCallback.accept(d);
            }
        }

        // Seen
        String seenField = isGroup ? "groupSeens" : "seens";
        JsonNode seenMsgs = data.path(seenField);
        if (seenMsgs.isArray() && seenMsgs.size() > 0 && seenCallback != null) {
            for (JsonNode s : seenMsgs) {
                seenCallback.accept(s);
            }
        }
    }
}
