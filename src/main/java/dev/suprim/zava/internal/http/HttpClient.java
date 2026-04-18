package dev.suprim.zava.internal.http;

import dev.suprim.zava.internal.session.Constants;
import dev.suprim.zava.internal.session.Context;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client wrapper around OkHttp.
 *
 * <p>Attaches default headers (User-Agent, Origin, Referer, Accept, etc.)
 * and manages cookies through the {@link ZavaCookieJar}.
 *
 * <p>Equivalent to zca-js {@code request()} + {@code getDefaultHeaders()}.
 */
public class HttpClient {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

    private final OkHttpClient client;
    private final Context context;

    public HttpClient(Context context) {
        this.context = context;

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .cookieJar(context.getCookieJar())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)    // Handle redirects manually like zca-js
                .followSslRedirects(false);

        Proxy proxy = context.getOptions().getProxy();
        if (proxy != null) {
            builder.proxy(proxy);
        }

        this.client = builder.build();
    }

    /**
     * GET request.
     */
    public Response get(String url) throws IOException {
        Request request = newRequestBuilder(url)
                .get()
                .build();
        return execute(request);
    }

    /**
     * POST with form-urlencoded body.
     */
    public Response post(String url, Map<String, String> formParams) throws IOException {
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : formParams.entrySet()) {
            formBuilder.add(entry.getKey(), entry.getValue());
        }

        Request request = newRequestBuilder(url)
                .post(formBuilder.build())
                .build();
        return execute(request);
    }

    /**
     * POST with multipart body (file upload).
     */
    public Response postMultipart(String url, MultipartBody body) throws IOException {
        Request request = newRequestBuilder(url)
                .post(body)
                .build();
        return execute(request);
    }

    /**
     * POST with raw body and custom content type.
     */
    public Response post(String url, String body, MediaType contentType) throws IOException {
        Request request = newRequestBuilder(url)
                .post(RequestBody.create(body, contentType))
                .build();
        return execute(request);
    }

    /**
     * Get the underlying OkHttpClient (for WebSocket upgrades).
     */
    public OkHttpClient getOkHttpClient() {
        return client;
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private Request.Builder newRequestBuilder(String url) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Encoding", "gzip, deflate, br, zstd")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Origin", Constants.ORIGIN)
                .header("Referer", Constants.REFERER);

        if (context.getUserAgent() != null) {
            builder.header("User-Agent", context.getUserAgent());
        }

        return builder;
    }

    private Response execute(Request request) throws IOException {
        log.debug("{} {}", request.method(), request.url());
        Response response = client.newCall(request).execute();

        // Handle redirects manually (matching zca-js behavior)
        String location = response.header("Location");
        if (location != null && response.code() >= 300 && response.code() < 400) {
            response.close();
            log.debug("Redirect -> {}", location);
            Request redirect = request.newBuilder()
                    .url(location)
                    .method("GET", null)
                    .header("Referer", "https://id.zalo.me/")
                    .build();
            return client.newCall(redirect).execute();
        }

        return response;
    }
}
