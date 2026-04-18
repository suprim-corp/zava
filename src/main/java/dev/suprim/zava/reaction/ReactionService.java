package dev.suprim.zava.reaction;

import com.fasterxml.jackson.databind.JsonNode;
import dev.suprim.zava.conversation.ThreadType;
import dev.suprim.zava.internal.http.BaseService;
import dev.suprim.zava.internal.http.HttpClient;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Context;

import java.util.*;

/**
 * Reaction operations: add reactions to messages.
 */
public class ReactionService extends BaseService {

    public ReactionService(Context context, HttpClient http, ResponseHandler responseHandler) {
        super(context, http, responseHandler);
    }

    /**
     * Add a reaction to a message.
     *
     * @param reaction the reaction type
     * @param msgId    the global message ID
     * @param cliMsgId the client message ID
     * @param threadId the thread ID (user or group)
     * @param type     USER or GROUP
     * @return raw response with msgIds
     */
    public JsonNode addReaction(Reaction reaction, String msgId, String cliMsgId,
                                String threadId, ThreadType type) {
        Map<String, Object> rMsg = new LinkedHashMap<>();
        rMsg.put("gMsgID", Long.parseLong(msgId));
        rMsg.put("cMsgID", Long.parseLong(cliMsgId));
        rMsg.put("msgType", 1);

        Map<String, Object> messageContent = new LinkedHashMap<>();
        messageContent.put("rMsg", List.of(rMsg));
        messageContent.put("rIcon", reaction.getIcon());
        messageContent.put("rType", reaction.getRType());
        messageContent.put("source", 6);

        try {
            Map<String, Object> reactEntry = new LinkedHashMap<>();
            reactEntry.put("message", MAPPER.writeValueAsString(messageContent));
            reactEntry.put("clientId", System.currentTimeMillis());

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("react_list", List.of(reactEntry));

            if (type == ThreadType.USER) {
                params.put("toid", threadId);
            } else {
                params.put("grid", threadId);
                params.put("imei", context.getImei());
            }

            String path = type == ThreadType.USER
                    ? "/api/message/reaction"
                    : "/api/group/reaction";
            return encryptedPostRaw("reaction", path, params);
        } catch (Exception e) {
            throw new dev.suprim.zava.exception.ZavaException("Failed to add reaction", e);
        }
    }

    /**
     * Standard reaction types.
     */
    public enum Reaction {
        LIKE(3, "\uD83D\uDC4D"),
        HEART(5, "❤"),
        HAHA(0, "\uD83D\uDE06"),
        WOW(32, "\uD83D\uDE2E"),
        CRY(2, "\uD83D\uDE22"),
        ANGRY(20, "\uD83D\uDE20"),
        SAD(1, "\uD83D\uDE1E");

        private final int rType;
        private final String icon;

        Reaction(int rType, String icon) {
            this.rType = rType;
            this.icon = icon;
        }

        public int getRType() { return rType; }
        public String getIcon() { return icon; }
    }
}
