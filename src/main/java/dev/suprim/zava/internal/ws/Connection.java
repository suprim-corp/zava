package dev.suprim.zava.internal.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.suprim.zava.internal.http.ResponseHandler;
import dev.suprim.zava.internal.session.Constants;
import dev.suprim.zava.internal.session.Context;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * WebSocket connection lifecycle manager.
 *
 * <p>Handles connecting, cipher key exchange, ping keepalive,
 * reconnection with endpoint rotation, and dispatches decoded frames
 * to a callback.
 */
public class Connection extends WebSocketListener {

    private static final Logger log = LoggerFactory.getLogger(Connection.class);
    private static final ObjectMapper MAPPER = ResponseHandler.mapper();

    private final Context context;
    private final OkHttpClient httpClient;
    private final List<String> wsUrls;

    private final AtomicReference<WebSocket> ws = new AtomicReference<>();
    private final AtomicReference<String> cipherKey = new AtomicReference<>();
    private final AtomicInteger reqIdCounter = new AtomicInteger(0);
    private final AtomicInteger rotateIndex = new AtomicInteger(0);

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pingTask;

    // Callbacks
    private Consumer<Frame> frameCallback;
    private Consumer<String> cipherKeyCallback;
    private BiConsumer<Integer, String> closedCallback;
    private Consumer<Throwable> errorCallback;
    private Runnable connectedCallback;

    private boolean retryOnClose;
    private volatile boolean stopped;

    public Connection(Context context, OkHttpClient httpClient) {
        this.context = context;
        this.httpClient = httpClient;
        this.wsUrls = context.getWsUrls();
    }

    // ── Callback setters ─────────────────────────────────────────────────

    public void onFrame(Consumer<Frame> callback) { this.frameCallback = callback; }
    public void onCipherKey(Consumer<String> callback) { this.cipherKeyCallback = callback; }
    public void onClosed(BiConsumer<Integer, String> callback) { this.closedCallback = callback; }
    public void onError(Consumer<Throwable> callback) { this.errorCallback = callback; }
    public void onConnected(Runnable callback) { this.connectedCallback = callback; }

    public String getCipherKey() { return cipherKey.get(); }

    // ── Connect / Disconnect ─────────────────────────────────────────────

    /**
     * Connect to the WebSocket.
     *
     * @param retryOnClose whether to auto-reconnect on close
     */
    public void connect(boolean retryOnClose) {
        if (ws.get() != null) {
            throw new IllegalStateException("Already connected");
        }

        this.retryOnClose = retryOnClose;
        this.stopped = false;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "zava-ws-scheduler");
            t.setDaemon(true);
            return t;
        });

        doConnect();
    }

    /**
     * Disconnect gracefully.
     */
    public void disconnect() {
        stopped = true;
        WebSocket socket = ws.getAndSet(null);
        if (socket != null) {
            socket.close(1000, "Manual closure");
        }
        reset();
    }

    /**
     * Send a raw frame.
     */
    public void send(Frame frame) {
        WebSocket socket = ws.get();
        if (socket != null) {
            socket.send(ByteString.of(frame.encode()));
        }
    }

    /**
     * Send a command with JSON data, auto-incrementing req_id.
     */
    public void sendCommand(int cmd, int subCmd, JsonNode data) {
        ObjectNode dataWithId = data.deepCopy();
        dataWithId.put("req_id", "req_" + reqIdCounter.getAndIncrement());

        String payload = dataWithId.toString();
        send(new Frame(1, cmd, subCmd, payload));
    }

    // ── WebSocketListener ────────────────────────────────────────────────

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        log.info("WebSocket connected");
        if (connectedCallback != null) connectedCallback.run();
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        byte[] data = bytes.toByteArray();
        if (data.length < Frame.HEADER_SIZE) return;

        try {
            Frame frame = Frame.decode(data);

            // Handle cipher key exchange
            if (frame.getVersion() == 1 && frame.getCmd() == 1 && frame.getSubCmd() == 1) {
                handleCipherKey(frame);
                return;
            }

            // Handle duplicate connection
            if (frame.getVersion() == 1 && frame.getCmd() == 3000 && frame.getSubCmd() == 0) {
                log.warn("Duplicate connection detected, closing");
                webSocket.close(3000, "Duplicate connection");
                return;
            }

            // Dispatch to frame callback
            if (frameCallback != null) {
                frameCallback.accept(frame);
            }

        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
            if (errorCallback != null) errorCallback.accept(e);
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        log.info("WebSocket closing: {} {}", code, reason);
        webSocket.close(code, reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        log.info("WebSocket closed: {} {}", code, reason);
        ws.set(null);
        reset();

        if (!stopped && retryOnClose) {
            // Rotate endpoint and reconnect
            int nextIndex = rotateIndex.incrementAndGet();
            if (nextIndex < wsUrls.size()) {
                log.info("Reconnecting with rotated endpoint...");
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "zava-ws-scheduler");
                    t.setDaemon(true);
                    return t;
                });
                scheduler.schedule(this::doConnect, 2, TimeUnit.SECONDS);
            } else {
                rotateIndex.set(0);
                if (closedCallback != null) closedCallback.accept(code, reason);
            }
        } else {
            if (closedCallback != null) closedCallback.accept(code, reason);
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        log.error("WebSocket failure", t);
        ws.set(null);
        reset();

        if (errorCallback != null) errorCallback.accept(t);

        if (!stopped && retryOnClose) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread th = new Thread(r, "zava-ws-scheduler");
                th.setDaemon(true);
                return th;
            });
            scheduler.schedule(this::doConnect, 3, TimeUnit.SECONDS);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private void doConnect() {
        int index = rotateIndex.get() % Math.max(1, wsUrls.size());
        String wsUrl = wsUrls.isEmpty()
                ? "wss://chat.zalo.me/ws"
                : wsUrls.get(index);

        // Append timestamp
        String sep = wsUrl.contains("?") ? "&" : "?";
        wsUrl = wsUrl + sep + "t=" + System.currentTimeMillis()
                + "&zpw_ver=" + context.getOptions().getApiVersion()
                + "&zpw_type=" + context.getOptions().getApiType();

        Request request = new Request.Builder()
                .url(wsUrl)
                .header("Origin", Constants.ORIGIN)
                .header("User-Agent", context.getUserAgent())
                .header("Accept-Encoding", "gzip, deflate, br, zstd")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .build();

        log.info("Connecting to WebSocket: {}", wsUrl);
        WebSocket socket = httpClient.newWebSocket(request, this);
        ws.set(socket);
    }

    private void handleCipherKey(Frame frame) {
        try {
            JsonNode parsed = MAPPER.readTree(frame.getPayload());
            String key = parsed.path("key").asText(null);
            if (key != null) {
                cipherKey.set(key);
                log.info("Cipher key received");
                if (cipherKeyCallback != null) cipherKeyCallback.accept(key);
                startPing();
            }
        } catch (Exception e) {
            log.error("Failed to parse cipher key", e);
        }
    }

    private void startPing() {
        if (pingTask != null) pingTask.cancel(false);

        int interval = 30_000; // default 30s
        if (context.getSettings() != null
                && context.getSettings().getFeatures() != null
                && context.getSettings().getFeatures().getSocket() != null) {
            int configured = context.getSettings().getFeatures().getSocket().getPingInterval();
            if (configured > 0) interval = configured;
        }

        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("eventId", System.currentTimeMillis());
                Frame ping = new Frame(1, Command.PING.getCmd(), Command.PING.getSubCmd(),
                        data.toString());
                send(ping);
            } catch (Exception e) {
                log.error("Ping failed", e);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void reset() {
        cipherKey.set(null);
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
