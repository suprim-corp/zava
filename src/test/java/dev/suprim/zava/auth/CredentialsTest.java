package dev.suprim.zava.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CredentialsTest {

    @Test
    @DisplayName("builder creates valid credentials")
    void builder() {
        List<Credentials.CookieEntry> cookies = Arrays.asList(
                new Credentials.CookieEntry("zpw_sek", "secret", "chat.zalo.me"),
                new Credentials.CookieEntry("zpsid", "session", "zalo.me")
        );

        Credentials creds = Credentials.builder()
                .imei("test-imei-123")
                .cookies(cookies)
                .userAgent("Mozilla/5.0 Test")
                .language("vi")
                .build();

        assertEquals("test-imei-123", creds.getImei());
        assertEquals(2, creds.getCookies().size());
        assertEquals("Mozilla/5.0 Test", creds.getUserAgent());
        assertEquals("vi", creds.getLanguage());
    }

    @Test
    @DisplayName("builder requires imei")
    void builderRequiresImei() {
        assertThrows(NullPointerException.class, () ->
                Credentials.builder()
                        .cookies(List.of())
                        .userAgent("UA")
                        .build());
    }

    @Test
    @DisplayName("builder requires cookies")
    void builderRequiresCookies() {
        assertThrows(NullPointerException.class, () ->
                Credentials.builder()
                        .imei("imei")
                        .userAgent("UA")
                        .build());
    }

    @Test
    @DisplayName("builder requires userAgent")
    void builderRequiresUserAgent() {
        assertThrows(NullPointerException.class, () ->
                Credentials.builder()
                        .imei("imei")
                        .cookies(List.of())
                        .build());
    }

    @Test
    @DisplayName("default language is vi")
    void defaultLanguage() {
        Credentials creds = Credentials.builder()
                .imei("imei")
                .cookies(List.of())
                .userAgent("UA")
                .build();
        assertEquals("vi", creds.getLanguage());
    }

    @Test
    @DisplayName("saveTo and loadFrom roundtrip")
    void saveAndLoad(@TempDir Path tempDir) throws IOException {
        Credentials.CookieEntry cookie = new Credentials.CookieEntry("zpw_sek", "abc", "chat.zalo.me");
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        cookie.setExpirationDate(1700000000L);

        Credentials original = Credentials.builder()
                .imei("my-imei")
                .cookies(List.of(cookie))
                .userAgent("Mozilla/5.0")
                .language("en")
                .build();

        Path file = tempDir.resolve("session.json");
        original.saveTo(file);

        Credentials loaded = Credentials.loadFrom(file);
        assertEquals("my-imei", loaded.getImei());
        assertEquals("Mozilla/5.0", loaded.getUserAgent());
        assertEquals("en", loaded.getLanguage());
        assertEquals(1, loaded.getCookies().size());
        assertEquals("zpw_sek", loaded.getCookies().get(0).getName());
        assertEquals("abc", loaded.getCookies().get(0).getValue());
        assertTrue(loaded.getCookies().get(0).isSecure());
        assertTrue(loaded.getCookies().get(0).isHttpOnly());
    }

    @Test
    @DisplayName("cookies list is immutable")
    void cookiesImmutable() {
        Credentials creds = Credentials.builder()
                .imei("imei")
                .cookies(Arrays.asList(new Credentials.CookieEntry("a", "b", "c")))
                .userAgent("UA")
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                creds.getCookies().add(new Credentials.CookieEntry("x", "y", "z")));
    }
}
