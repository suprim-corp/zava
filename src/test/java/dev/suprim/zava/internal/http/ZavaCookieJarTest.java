package dev.suprim.zava.internal.http;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZavaCookieJarTest {

    private static final HttpUrl ZALO_URL = HttpUrl.parse("https://chat.zalo.me/");

    @Test
    @DisplayName("saves and loads cookies for a URL")
    void saveAndLoad() {
        ZavaCookieJar jar = new ZavaCookieJar();

        Cookie cookie = new Cookie.Builder()
                .domain("chat.zalo.me")
                .path("/")
                .name("zpw_sek")
                .value("abc123")
                .build();

        jar.saveFromResponse(ZALO_URL, List.of(cookie));

        List<Cookie> loaded = jar.loadForRequest(ZALO_URL);
        assertEquals(1, loaded.size());
        assertEquals("zpw_sek", loaded.get(0).name());
        assertEquals("abc123", loaded.get(0).value());
    }

    @Test
    @DisplayName("replaces cookies with same name/domain/path")
    void replaceDuplicate() {
        ZavaCookieJar jar = new ZavaCookieJar();

        Cookie old = new Cookie.Builder()
                .domain("chat.zalo.me").path("/").name("tok").value("old").build();
        Cookie updated = new Cookie.Builder()
                .domain("chat.zalo.me").path("/").name("tok").value("new").build();

        jar.saveFromResponse(ZALO_URL, List.of(old));
        jar.saveFromResponse(ZALO_URL, List.of(updated));

        List<Cookie> loaded = jar.loadForRequest(ZALO_URL);
        assertEquals(1, loaded.size());
        assertEquals("new", loaded.get(0).value());
    }

    @Test
    @DisplayName("addCookies parses raw cookie strings")
    void addCookies() {
        ZavaCookieJar jar = new ZavaCookieJar();
        jar.addCookies(ZALO_URL, Arrays.asList(
                "zpw_sek=secret123",
                "zpsid=session456"
        ));

        List<Cookie> loaded = jar.loadForRequest(ZALO_URL);
        assertEquals(2, loaded.size());
    }

    @Test
    @DisplayName("getCookieHeader builds proper header string")
    void getCookieHeader() {
        ZavaCookieJar jar = new ZavaCookieJar();
        jar.addCookies(ZALO_URL, Arrays.asList("a=1", "b=2"));

        String header = jar.getCookieHeader(ZALO_URL);
        assertTrue(header.contains("a=1"));
        assertTrue(header.contains("b=2"));
        assertTrue(header.contains("; "));
    }

    @Test
    @DisplayName("loadForRequest returns empty for unknown host")
    void loadEmpty() {
        ZavaCookieJar jar = new ZavaCookieJar();
        HttpUrl other = HttpUrl.parse("https://other.example.com/");
        assertTrue(jar.loadForRequest(other).isEmpty());
    }

    @Test
    @DisplayName("clear removes all cookies")
    void clear() {
        ZavaCookieJar jar = new ZavaCookieJar();
        jar.addCookies(ZALO_URL, Arrays.asList("a=1"));
        assertFalse(jar.getAllCookies().isEmpty());

        jar.clear();
        assertTrue(jar.getAllCookies().isEmpty());
    }
}
