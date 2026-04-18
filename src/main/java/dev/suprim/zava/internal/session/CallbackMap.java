package dev.suprim.zava.internal.session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Thread-safe map with per-entry TTL for upload callbacks.
 *
 * <p>When a file upload completes, the server sends a WebSocket event
 * with the file ID. This map stores callbacks keyed by file ID, and
 * automatically removes entries after the TTL expires.
 *
 * <p>Equivalent to zca-js {@code CallbacksMap}.
 */
public class CallbackMap<V> {

    private final ConcurrentHashMap<String, V> map = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final long defaultTtlMs;

    /**
     * Create a CallbackMap with the default TTL from {@link Constants#UPLOAD_CALLBACK_TTL_MS}.
     */
    public CallbackMap() {
        this(Constants.UPLOAD_CALLBACK_TTL_MS);
    }

    /**
     * Create a CallbackMap with a custom default TTL.
     *
     * @param defaultTtlMs default time-to-live in milliseconds
     */
    public CallbackMap(long defaultTtlMs) {
        this.defaultTtlMs = defaultTtlMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "zava-callback-ttl");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Put a value with the default TTL.
     */
    public void put(String key, V value) {
        put(key, value, defaultTtlMs);
    }

    /**
     * Put a value with a custom TTL.
     */
    public void put(String key, V value, long ttlMs) {
        map.put(key, value);
        scheduler.schedule(() -> map.remove(key), ttlMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Get and remove a value.
     */
    public V remove(String key) {
        return map.remove(key);
    }

    /**
     * Get a value without removing it.
     */
    public V get(String key) {
        return map.get(key);
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public int size() {
        return map.size();
    }

    /**
     * Shutdown the TTL scheduler. Call when the client is closed.
     */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
