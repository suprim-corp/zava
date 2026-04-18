package dev.suprim.zava.internal.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.exception.ZavaException;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.session.Context;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Decrypts and parses API responses.
 *
 * <p>Standard Zalo API response format:
 * <pre>{@code
 * {
 *   "error_code": 0,
 *   "error_message": "...",
 *   "data": "<AES-encrypted JSON string>"
 * }
 * }</pre>
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Check HTTP status</li>
 *   <li>Parse outer JSON envelope</li>
 *   <li>Check outer {@code error_code}</li>
 *   <li>AES-CBC decrypt the {@code data} field using session secret key</li>
 *   <li>Parse inner JSON</li>
 *   <li>Check inner {@code error_code}</li>
 *   <li>Return typed result via Jackson</li>
 * </ol>
 *
 * <p>Equivalent to zca-js {@code handleZaloResponse()} + {@code resolveResponse()}.
 */
public class ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ResponseHandler.class);

    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Context context;

    public ResponseHandler(Context context) {
        this.context = context;
    }

    /**
     * Handle an encrypted API response.
     *
     * @param response    the HTTP response (will be closed)
     * @param resultType  the type to deserialize the inner {@code data} field into
     * @param <T>         result type
     * @return deserialized result
     * @throws ZavaException on HTTP error, API error, or decryption failure
     */
    public <T> T handle(Response response, Class<T> resultType) {
        return handle(response, resultType, true);
    }

    /**
     * Handle an API response with optional encryption.
     *
     * @param response    the HTTP response (will be closed)
     * @param resultType  the type to deserialize the inner {@code data} field into
     * @param encrypted   whether the {@code data} field is AES-encrypted
     * @param <T>         result type
     * @return deserialized result
     * @throws ZavaException on HTTP error, API error, or decryption failure
     */
    public <T> T handle(Response response, Class<T> resultType, boolean encrypted) {
        JsonNode dataNode = handleToNode(response, encrypted);
        try {
            return MAPPER.treeToValue(dataNode, resultType);
        } catch (Exception e) {
            throw new ZavaException("Failed to deserialize response data", e);
        }
    }

    /**
     * Handle an encrypted API response with a generic type.
     */
    public <T> T handle(Response response, TypeReference<T> resultType) {
        return handle(response, resultType, true);
    }

    /**
     * Handle an API response with a generic type and optional encryption.
     */
    public <T> T handle(Response response, TypeReference<T> resultType, boolean encrypted) {
        JsonNode dataNode = handleToNode(response, encrypted);
        try {
            return MAPPER.readValue(MAPPER.treeAsTokens(dataNode), MAPPER.constructType(resultType.getType()));
        } catch (Exception e) {
            throw new ZavaException("Failed to deserialize response data", e);
        }
    }

    /**
     * Handle a response and return raw JSON node (useful when you need dynamic parsing).
     */
    public JsonNode handleRaw(Response response, boolean encrypted) {
        return handleToNode(response, encrypted);
    }

    /**
     * Get the shared ObjectMapper.
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private JsonNode handleToNode(Response response, boolean encrypted) {
        try (response) {
            // 1. Check HTTP status
            if (!response.isSuccessful()) {
                throw new ZavaException(
                        "Request failed with status " + response.code(),
                        response.code());
            }

            // 2. Parse outer envelope
            ResponseBody body = response.body();
            if (body == null) {
                throw new ZavaException("Empty response body");
            }
            String bodyStr = body.string();
            JsonNode envelope = MAPPER.readTree(bodyStr);

            // 3. Check outer error_code
            int outerErrorCode = envelope.path("error_code").asInt(-1);
            if (outerErrorCode != 0) {
                String errorMsg = envelope.path("error_message").asText("Unknown error");
                throw new ZavaException(errorMsg, outerErrorCode);
            }

            // 4. Get data field
            JsonNode dataField = envelope.path("data");
            if (dataField.isMissingNode() || dataField.isNull()) {
                throw new ZavaException("Response has no data field");
            }

            JsonNode innerData;

            if (encrypted) {
                // 5. AES-CBC decrypt
                String encryptedData = dataField.asText();
                String decrypted = AesCbc.decodeAES(context.getSecretKey(), encryptedData);
                log.debug("Decrypted response: {}",
                        decrypted.length() > 200 ? decrypted.substring(0, 200) + "..." : decrypted);

                // 6. Parse inner JSON
                innerData = MAPPER.readTree(decrypted);

                // 7. Check inner error_code (if present)
                int innerErrorCode = innerData.path("error_code").asInt(0);
                if (innerErrorCode != 0) {
                    String errorMsg = innerData.path("error_message").asText("Unknown error");
                    throw new ZavaException(errorMsg, innerErrorCode);
                }

                // Return inner "data" if it exists, otherwise the whole inner object
                JsonNode innerDataField = innerData.path("data");
                return innerDataField.isMissingNode() ? innerData : innerDataField;
            } else {
                // Not encrypted — data field is already the result
                return dataField;
            }

        } catch (ZavaException e) {
            throw e;
        } catch (IOException e) {
            throw new ZavaException("Failed to read response", e);
        } catch (Exception e) {
            throw new ZavaException("Failed to process response", e);
        }
    }
}
