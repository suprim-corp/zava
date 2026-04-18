package dev.suprim.zava.internal.http;

import dev.suprim.zava.internal.session.Constants;
import dev.suprim.zava.internal.session.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * URL builder for Zalo API requests.
 *
 * <p>Equivalent to zca-js {@code makeURL(ctx, baseURL, params, apiVersion)}.
 * Automatically appends {@code zpw_ver} and {@code zpw_type} query parameters.
 */
public final class Urls {

    private Urls() {}

    /**
     * Build a URL with query parameters and automatic API version params.
     *
     * @param baseUrl    the base URL (e.g. service map URL + "/api/path")
     * @param params     additional query parameters (may be empty)
     * @param apiVersion if true, auto-append zpw_ver and zpw_type
     * @param context    the session context (for API_TYPE and API_VERSION)
     * @return the complete URL string
     */
    public static String build(String baseUrl, Map<String, ?> params,
                               boolean apiVersion, Context context) {
        StringBuilder url = new StringBuilder(baseUrl);
        Map<String, String> allParams = new LinkedHashMap<>();

        // Add user params
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                allParams.put(entry.getKey(), entry.getValue().toString());
            }
        }

        // Add API version params if requested
        if (apiVersion) {
            allParams.putIfAbsent("zpw_ver",
                    String.valueOf(context.getOptions().getApiVersion()));
            allParams.putIfAbsent("zpw_type",
                    String.valueOf(context.getOptions().getApiType()));
        }

        if (!allParams.isEmpty()) {
            url.append(baseUrl.contains("?") ? "&" : "?");
            boolean first = true;
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (!first) url.append("&");
                url.append(urlEncode(entry.getKey()))
                   .append("=")
                   .append(urlEncode(entry.getValue()));
                first = false;
            }
        }

        return url.toString();
    }

    /**
     * Build a URL with query parameters and automatic API version params.
     */
    public static String build(String baseUrl, Map<String, ?> params, Context context) {
        return build(baseUrl, params, true, context);
    }

    /**
     * Build a URL with only API version params (no extra params).
     */
    public static String build(String baseUrl, Context context) {
        return build(baseUrl, Map.of(), true, context);
    }

    /**
     * Build a service URL from the service map.
     *
     * @param context     the session context
     * @param serviceName the service name in the service map (e.g. "chat", "group")
     * @param apiPath     the API path (e.g. "/api/message/send")
     * @param params      additional query parameters
     * @return the complete URL string
     */
    public static String service(Context context, String serviceName,
                                 String apiPath, Map<String, ?> params) {
        String baseUrl = context.getServiceUrl(serviceName) + apiPath;
        return build(baseUrl, params, true, context);
    }

    /**
     * Build a service URL with no extra params.
     */
    public static String service(Context context, String serviceName, String apiPath) {
        return service(context, serviceName, apiPath, Map.of());
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always supported
            throw new RuntimeException(e);
        }
    }
}
