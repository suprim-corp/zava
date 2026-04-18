package dev.suprim.zava.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.conversation.ThreadType;
import dev.suprim.zava.exception.ZavaException;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.http.Urls;
import dev.suprim.zava.internal.session.Context;
import okhttp3.Response;

import java.io.IOException;
import java.util.*;

/**
 * Message operations: send, delete, undo, forward.
 *
 * <p>Handles both user (direct) and group threads. All API calls follow
 * the standard Zalo pattern: encrypt params → POST → decrypt response.
 *
 * <pre>{@code
 * client.messages().send("Hello!", threadId, ThreadType.USER);
 * client.messages().send("Hi group!", groupId, ThreadType.GROUP);
 * }</pre>
 */
public class MessageService {

    private static final ObjectMapper MAPPER = ResponseHandler.mapper();

    private final Context context;
    private final HttpClient http;
    private final ResponseHandler responseHandler;

    public MessageService(Context context, HttpClient http, ResponseHandler responseHandler) {
        this.context = context;
        this.http = http;
        this.responseHandler = responseHandler;
    }

    // ── Send ─────────────────────────────────────────────────────────────

    /**
     * Send a text message.
     *
     * @param message  the text content
     * @param threadId the recipient user ID or group ID
     * @param type     USER or GROUP
     * @return send result containing the message ID
     */
    public SendResult send(String message, String threadId, ThreadType type) {
        return send(message, threadId, type, null, null, 0);
    }

    /**
     * Send a text message with mentions (group only).
     *
     * @param message  the text content
     * @param threadId the group ID
     * @param type     must be GROUP
     * @param mentions list of @mentions
     * @return send result
     */
    public SendResult send(String message, String threadId, ThreadType type, List<Mention> mentions) {
        return send(message, threadId, type, mentions, null, 0);
    }

    /**
     * Send a text message with optional mentions and quote.
     *
     * @param message  the text content
     * @param threadId the recipient user ID or group ID
     * @param type     USER or GROUP
     * @param mentions list of @mentions (group only, may be null)
     * @param quote    quoted message reference (may be null)
     * @param ttl      time-to-live in seconds (0 = no expiry)
     * @return send result
     */
    public SendResult send(String message, String threadId, ThreadType type,
                           List<Mention> mentions, Quote quote, int ttl) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(type, "type must not be null");

        try {
            Map<String, Object> params = new LinkedHashMap<>();

            if (quote != null) {
                // Quote message
                if (type == ThreadType.USER) {
                    params.put("toid", threadId);
                } else {
                    params.put("grid", threadId);
                    params.put("visibility", 0);
                }
                params.put("message", message);
                params.put("clientId", System.currentTimeMillis());

                if (type == ThreadType.GROUP && mentions != null && !mentions.isEmpty()) {
                    params.put("mentionInfo", MAPPER.writeValueAsString(mentions));
                }

                params.put("qmsgOwner", quote.getOwnerId());
                params.put("qmsgId", quote.getGlobalMsgId());
                params.put("qmsgCliId", quote.getCliMsgId());
                params.put("qmsgType", quote.getCliMsgType());
                params.put("qmsgTs", quote.getTs());
                params.put("qmsg", quote.getMsg());
                params.put("qmsgAttach", quote.getAttach() != null ? quote.getAttach() : "");
                params.put("qmsgTTL", quote.getTtl());

                if (type == ThreadType.USER) {
                    params.put("imei", context.getImei());
                }
                if (ttl > 0) params.put("ttl", ttl);

            } else {
                // Normal message
                params.put("message", message);
                params.put("clientId", System.currentTimeMillis());

                if (type == ThreadType.GROUP && mentions != null && !mentions.isEmpty()) {
                    params.put("mentionInfo", MAPPER.writeValueAsString(mentions));
                }

                if (type == ThreadType.USER) {
                    params.put("imei", context.getImei());
                    params.put("toid", threadId);
                } else {
                    params.put("visibility", 0);
                    params.put("grid", threadId);
                }
                if (ttl > 0) params.put("ttl", ttl);
            }

            // Determine endpoint
            String service = type == ThreadType.USER ? "chat" : "group";
            String path;
            if (quote != null) {
                path = type == ThreadType.USER
                        ? "/api/message/quote"
                        : "/api/group/quote";
            } else if (mentions != null && !mentions.isEmpty()) {
                path = type == ThreadType.USER
                        ? "/api/message/sms"   // user doesn't have /mention
                        : "/api/group/mention";
            } else {
                path = type == ThreadType.USER
                        ? "/api/message/sms"
                        : "/api/group/sendmsg";
            }

            return doPost(service, path, params);

        } catch (ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaException("Failed to send message", e);
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────

    /**
     * Delete a message.
     *
     * @param msgId    the global message ID
     * @param cliMsgId the client message ID
     * @param uidFrom  the sender's UID
     * @param threadId the thread ID
     * @param type     USER or GROUP
     * @param onlyMe   if true, delete only for yourself; if false, delete for everyone
     * @return the operation result
     */
    public JsonNode delete(String msgId, String cliMsgId, String uidFrom,
                           String threadId, ThreadType type, boolean onlyMe) {
        Objects.requireNonNull(msgId, "msgId must not be null");
        Objects.requireNonNull(threadId, "threadId must not be null");

        try {
            Map<String, Object> params = new LinkedHashMap<>();

            if (type == ThreadType.USER) {
                params.put("toid", threadId);
            } else {
                params.put("grid", threadId);
            }

            params.put("cliMsgId", System.currentTimeMillis());

            // Build msgs array
            Map<String, Object> msgEntry = new LinkedHashMap<>();
            msgEntry.put("cliMsgId", cliMsgId != null ? cliMsgId : "0");
            msgEntry.put("globalMsgId", msgId);
            msgEntry.put("ownerId", uidFrom);
            msgEntry.put("destId", threadId);
            params.put("msgs", List.of(msgEntry));

            params.put("onlyMe", onlyMe ? 1 : 0);

            if (type == ThreadType.USER) {
                params.put("imei", context.getImei());
            }

            String service = type == ThreadType.USER ? "chat" : "group";
            String path = type == ThreadType.USER
                    ? "/api/message/delete"
                    : "/api/group/deletemsg";

            return doPostRaw(service, path, params);

        } catch (ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaException("Failed to delete message", e);
        }
    }

    // ── Undo ─────────────────────────────────────────────────────────────

    /**
     * Undo (recall) a sent message.
     *
     * <p>Only works for messages sent by the logged-in user, within the undo time window.
     *
     * @param msgId    the global message ID
     * @param cliMsgId the client message ID
     * @param threadId the thread ID
     * @param type     USER or GROUP
     * @return the operation result
     */
    public JsonNode undo(String msgId, String cliMsgId,
                         String threadId, ThreadType type) {
        Objects.requireNonNull(msgId, "msgId must not be null");
        Objects.requireNonNull(threadId, "threadId must not be null");

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("msgId", msgId);
            params.put("clientId", System.currentTimeMillis());
            params.put("cliMsgIdUndo", cliMsgId != null ? cliMsgId : "0");

            if (type == ThreadType.GROUP) {
                params.put("grid", threadId);
                params.put("visibility", 0);
                params.put("imei", context.getImei());
            } else {
                params.put("toid", threadId);
            }

            String service = type == ThreadType.USER ? "chat" : "group";
            String path = type == ThreadType.USER
                    ? "/api/message/undo"
                    : "/api/group/undomsg";

            return doPostRaw(service, path, params);

        } catch (ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaException("Failed to undo message", e);
        }
    }

    // ── Forward ──────────────────────────────────────────────────────────

    /**
     * Forward a message to one or more threads.
     *
     * @param message       the message text to forward
     * @param targetIds     list of target thread IDs
     * @param type          USER or GROUP
     * @param originalMsgId the original message ID being forwarded
     * @param ttl           time-to-live in seconds (0 = no expiry)
     * @return the operation result
     */
    public JsonNode forward(String message, List<String> targetIds, ThreadType type,
                            String originalMsgId, int ttl) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(targetIds, "targetIds must not be null");
        if (targetIds.isEmpty()) {
            throw new ZavaException("targetIds must not be empty");
        }

        try {
            Map<String, Object> params = new LinkedHashMap<>();

            long clientId = System.currentTimeMillis();

            if (type == ThreadType.USER) {
                List<Map<String, Object>> toIds = new ArrayList<>();
                for (String id : targetIds) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("clientId", clientId++);
                    entry.put("toUid", id);
                    entry.put("ttl", ttl);
                    toIds.add(entry);
                }
                params.put("toIds", toIds);
                params.put("imei", context.getImei());
            } else {
                List<Map<String, Object>> grids = new ArrayList<>();
                for (String id : targetIds) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("clientId", clientId++);
                    entry.put("grid", id);
                    entry.put("ttl", ttl);
                    grids.add(entry);
                }
                params.put("grids", grids);
            }

            params.put("ttl", ttl);
            params.put("msgType", "1");
            params.put("totalIds", targetIds.size());

            // msgInfo
            Map<String, Object> msgInfo = new LinkedHashMap<>();
            msgInfo.put("message", message);
            params.put("msgInfo", MAPPER.writeValueAsString(msgInfo));

            String path = type == ThreadType.USER
                    ? "/api/message/mforward"
                    : "/api/group/mforward";

            return doPostRaw("file", path, params);

        } catch (ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaException("Failed to forward message", e);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private SendResult doPost(String service, String path, Map<String, Object> params)
            throws IOException {
        String encrypted = AesCbc.encodeAES(
                context.getSecretKey(),
                MAPPER.writeValueAsString(params));

        String url = Urls.service(context, service, path);
        Response response = http.post(url, Map.of("params", encrypted));
        return responseHandler.handle(response, SendResult.class);
    }

    private JsonNode doPostRaw(String service, String path, Map<String, Object> params)
            throws IOException {
        String encrypted = AesCbc.encodeAES(
                context.getSecretKey(),
                MAPPER.writeValueAsString(params));

        String url = Urls.service(context, service, path);
        Response response = http.post(url, Map.of("params", encrypted));
        return responseHandler.handleRaw(response, true);
    }

    // ── Typing / Seen / Delivered Events ─────────────────────────────────

    /**
     * Send a typing indicator.
     *
     * @param threadId the thread ID
     * @param type     USER or GROUP
     */
    public JsonNode sendTypingEvent(String threadId, ThreadType type) {
        Objects.requireNonNull(threadId, "threadId must not be null");

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            if (type == ThreadType.USER) {
                params.put("toid", threadId);
                params.put("destType", 3);
            } else {
                params.put("grid", threadId);
                params.put("destType", 1);
            }
            params.put("imei", context.getImei());

            String service = type == ThreadType.USER ? "chat" : "group";
            String path = type == ThreadType.USER
                    ? "/api/message/typing"
                    : "/api/group/typing";
            return doPostRaw(service, path, params);
        } catch (ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaException("Failed to send typing event", e);
        }
    }

    /**
     * Send seen receipt for messages.
     *
     * @param msgId     the global message ID
     * @param cliMsgId  the client message ID
     * @param senderId  the sender UID (for user) or null (for group)
     * @param threadId  the thread ID (for group, this is the grid)
     * @param type      USER or GROUP
     */
    public JsonNode sendSeenEvent(String msgId, String cliMsgId,
                                  String senderId, String threadId, ThreadType type) {
        Objects.requireNonNull(msgId, "msgId must not be null");

        try {
            Map<String, Object> msgInfo = new LinkedHashMap<>();
            msgInfo.put("cliMsgId", cliMsgId != null ? cliMsgId : "0");
            msgInfo.put("globalMsgId", msgId);
            msgInfo.put("cliMsgType", 1);

            Map<String, Object> params = new LinkedHashMap<>();
            if (type == ThreadType.USER) {
                msgInfo.put("senderId", senderId);
                params.put("msgInfos", MAPPER.writeValueAsString(
                        Map.of("data", List.of(msgInfo))));
            } else {
                params.put("msgInfos", MAPPER.writeValueAsString(
                        Map.of("data", List.of(msgInfo), "grid", threadId)));
            }
            params.put("imei", context.getImei());

            String service = type == ThreadType.USER ? "chat" : "group";
            String path = type == ThreadType.USER
                    ? "/api/message/seenv2"
                    : "/api/group/seenv2";
            return doPostRaw(service, path, params);
        } catch (ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaException("Failed to send seen event", e);
        }
    }

    /**
     * Send delivered receipt for messages.
     */
    public JsonNode sendDeliveredEvent(String msgId, String threadId, ThreadType type) {
        Objects.requireNonNull(msgId, "msgId must not be null");

        try {
            Map<String, Object> msgInfo = new LinkedHashMap<>();
            msgInfo.put("msgId", msgId);

            Map<String, Object> params = new LinkedHashMap<>();
            if (type == ThreadType.USER) {
                params.put("msgInfos", MAPPER.writeValueAsString(
                        Map.of("seen", 0, "data", List.of(msgInfo))));
            } else {
                params.put("msgInfos", MAPPER.writeValueAsString(
                        Map.of("seen", 0, "data", List.of(msgInfo), "grid", threadId)));
            }
            params.put("imei", context.getImei());

            String service = type == ThreadType.USER ? "chat" : "group";
            String path = type == ThreadType.USER
                    ? "/api/message/deliveredv2"
                    : "/api/group/deliveredv2";
            return doPostRaw(service, path, params);
        } catch (ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaException("Failed to send delivered event", e);
        }
    }

    /**
     * Send a sticker.
     *
     * @param stickerId the sticker ID
     * @param cateId    the sticker category ID
     * @param threadId  the thread ID
     * @param type      USER or GROUP
     */
    public SendResult sendSticker(int stickerId, int cateId, String threadId, ThreadType type) {
        Objects.requireNonNull(threadId, "threadId must not be null");

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("stickerId", stickerId);
            params.put("cateId", cateId);
            params.put("type", 7);
            params.put("clientId", System.currentTimeMillis());
            params.put("imei", context.getImei());
            params.put("zsource", -1);

            if (type == ThreadType.USER) {
                params.put("toid", threadId);
            } else {
                params.put("grid", threadId);
                params.put("visibility", 0);
            }

            String service = type == ThreadType.USER ? "chat" : "group";
            String path = type == ThreadType.USER
                    ? "/api/message/sticker"
                    : "/api/group/sticker";
            return doPost(service, path, params);
        } catch (ZavaException e) {
            throw e;
        } catch (Exception e) {
            throw new ZavaException("Failed to send sticker", e);
        }
    }
}
