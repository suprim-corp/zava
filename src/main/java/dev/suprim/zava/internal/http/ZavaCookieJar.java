package dev.suprim.zava.internal.http;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OkHttp {@link CookieJar} implementation that stores cookies in memory
 * and supports initializing from raw cookie strings.
 *
 * <p>Thread-safe: backed by {@link ConcurrentHashMap}.
 */
public class ZavaCookieJar implements CookieJar {

    /**
     * Cookies stored by host → list of cookies.
     */
    private final ConcurrentHashMap<String, List<Cookie>> store = new ConcurrentHashMap<>();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        if (cookies.isEmpty()) return;
        store.merge(url.host(), new ArrayList<>(cookies), (existing, incoming) -> {
            // Replace cookies with same name, add new ones
            List<Cookie> merged = new ArrayList<>(existing);
            for (Cookie newCookie : incoming) {
                merged.removeIf(c -> c.name().equals(newCookie.name())
                        && c.domain().equals(newCookie.domain())
                        && c.path().equals(newCookie.path()));
                merged.add(newCookie);
            }
            return merged;
        });
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> cookies = store.get(url.host());
        if (cookies == null) return Collections.emptyList();

        // Filter out expired cookies
        long now = System.currentTimeMillis();
        List<Cookie> valid = new ArrayList<>();
        for (Cookie cookie : cookies) {
            if (cookie.expiresAt() >= now) {
                valid.add(cookie);
            }
        }
        return valid;
    }

    /**
     * Add cookies from raw "name=value" strings for a specific URL.
     *
     * <p>Used when initializing from user-provided credentials.
     *
     * @param url           the URL context for cookie parsing
     * @param cookieStrings raw cookie strings (e.g. "zpw_sek=abc123")
     */
    public void addCookies(HttpUrl url, List<String> cookieStrings) {
        List<Cookie> cookies = new ArrayList<>();
        for (String raw : cookieStrings) {
            Cookie cookie = Cookie.parse(url, raw);
            if (cookie != null) {
                cookies.add(cookie);
            }
        }
        if (!cookies.isEmpty()) {
            saveFromResponse(url, cookies);
        }
    }

    /**
     * Build a "Cookie" header string for a URL.
     *
     * @param url the request URL
     * @return cookie header value (e.g. "name1=val1; name2=val2"), or empty string
     */
    public String getCookieHeader(HttpUrl url) {
        List<Cookie> cookies = loadForRequest(url);
        if (cookies.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cookies.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(cookies.get(i).name()).append("=").append(cookies.get(i).value());
        }
        return sb.toString();
    }

    /**
     * Get all stored cookies across all hosts.
     */
    public List<Cookie> getAllCookies() {
        List<Cookie> all = new ArrayList<>();
        for (List<Cookie> hostCookies : store.values()) {
            all.addAll(hostCookies);
        }
        return all;
    }

    /**
     * Clear all stored cookies.
     */
    public void clear() {
        store.clear();
    }
}
