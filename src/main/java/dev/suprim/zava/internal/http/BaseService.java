package dev.suprim.zava.internal.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.suprim.zava.exception.ZavaException;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.session.Context;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

/**
 * Base class for all domain services.
 *
 * <p>Provides standard encrypt → POST/GET → decrypt helpers
 * that all API services share.
 */
public abstract class BaseService {

    protected static final ObjectMapper MAPPER = ResponseHandler.mapper();

    protected final Context context;
    protected final HttpClient http;
    protected final ResponseHandler responseHandler;

    protected BaseService(Context context, HttpClient http, ResponseHandler responseHandler) {
        this.context = context;
        this.http = http;
        this.responseHandler = responseHandler;
    }

    /**
     * POST with encrypted params in body. Decrypt response.
     */
    protected <T> T encryptedPost(String service, String path,
                                   Map<String, Object> params, Class<T> resultType) {
        try {
            String encrypted = AesCbc.encodeAES(
                    context.getSecretKey(), MAPPER.writeValueAsString(params));
            String url = Urls.service(context, service, path);
            Response response = http.post(url, Map.of("params", encrypted));
            return responseHandler.handle(response, resultType);
        } catch (ZavaException e) {
            throw e;
        } catch (IOException e) {
            throw new ZavaException("Request failed", e);
        }
    }

    /**
     * POST with encrypted params in body. Return raw JsonNode.
     */
    protected JsonNode encryptedPostRaw(String service, String path, Map<String, Object> params) {
        try {
            String encrypted = AesCbc.encodeAES(
                    context.getSecretKey(), MAPPER.writeValueAsString(params));
            String url = Urls.service(context, service, path);
            Response response = http.post(url, Map.of("params", encrypted));
            return responseHandler.handleRaw(response, true);
        } catch (ZavaException e) {
            throw e;
        } catch (IOException e) {
            throw new ZavaException("Request failed", e);
        }
    }

    /**
     * GET with encrypted params in query string. Decrypt response.
     */
    protected <T> T encryptedGet(String service, String path,
                                  Map<String, Object> params, Class<T> resultType) {
        try {
            String encrypted = AesCbc.encodeAES(
                    context.getSecretKey(), MAPPER.writeValueAsString(params));
            String url = Urls.service(context, service, path, Map.of("params", encrypted));
            Response response = http.get(url);
            return responseHandler.handle(response, resultType);
        } catch (ZavaException e) {
            throw e;
        } catch (IOException e) {
            throw new ZavaException("Request failed", e);
        }
    }

    /**
     * GET with encrypted params in query string. Return raw JsonNode.
     */
    protected JsonNode encryptedGetRaw(String service, String path, Map<String, Object> params) {
        try {
            String encrypted = AesCbc.encodeAES(
                    context.getSecretKey(), MAPPER.writeValueAsString(params));
            String url = Urls.service(context, service, path, Map.of("params", encrypted));
            Response response = http.get(url);
            return responseHandler.handleRaw(response, true);
        } catch (ZavaException e) {
            throw e;
        } catch (IOException e) {
            throw new ZavaException("Request failed", e);
        }
    }

    /**
     * GET with no params. Decrypt response.
     */
    protected JsonNode simpleGetRaw(String service, String path) {
        try {
            String url = Urls.service(context, service, path);
            Response response = http.get(url);
            return responseHandler.handleRaw(response, true);
        } catch (ZavaException e) {
            throw e;
        } catch (IOException e) {
            throw new ZavaException("Request failed", e);
        }
    }

    /**
     * GET with no params. Unencrypted response.
     */
    protected JsonNode simpleGetUnencrypted(String service, String path) {
        try {
            String url = Urls.service(context, service, path);
            Response response = http.get(url);
            return responseHandler.handleRaw(response, false);
        } catch (ZavaException e) {
            throw e;
        } catch (IOException e) {
            throw new ZavaException("Request failed", e);
        }
    }
}
