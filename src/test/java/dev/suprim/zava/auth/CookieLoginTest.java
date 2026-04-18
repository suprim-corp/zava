package dev.suprim.zava.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.exception.ZavaAuthException;
import dev.suprim.zava.internal.crypto.AesCbc;
import dev.suprim.zava.internal.session.Context;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CookieLoginTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // The encrypt key returned by ParamsEncryptor is dynamic, so we can't predict it.
    // Instead, we test the full flow by mocking getLoginInfo to return unencrypted data
    // when encryption is "off" — but zca-js always encrypts.
    //
    // For a proper integration test, we mock the server to:
    // 1. Accept any getLoginInfo request and return a known encrypted response
    // 2. Accept any getServerInfo request and return settings

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("login throws on missing imei")
    void loginMissingImei() {
        CookieLogin login = new CookieLogin(ZavaOptions.defaults());

        assertThrows(NullPointerException.class, () ->
                Credentials.builder()
                        .cookies(List.of(new Credentials.CookieEntry("a", "b", "c")))
                        .userAgent("UA")
                        .build());
    }

    @Test
    @DisplayName("login throws on empty cookies")
    void loginEmptyCookies() {
        CookieLogin login = new CookieLogin(ZavaOptions.defaults());

        Credentials creds = Credentials.builder()
                .imei("imei")
                .cookies(List.of())
                .userAgent("UA")
                .build();

        assertThrows(ZavaAuthException.class, () -> login.login(creds));
    }

    @Test
    @DisplayName("login throws on empty imei string")
    void loginEmptyImei() {
        CookieLogin login = new CookieLogin(ZavaOptions.defaults());

        Credentials creds = Credentials.builder()
                .imei("")
                .cookies(List.of(new Credentials.CookieEntry("a", "b", "c")))
                .userAgent("UA")
                .build();

        assertThrows(ZavaAuthException.class, () -> login.login(creds));
    }

    @Test
    @DisplayName("login throws on empty userAgent string")
    void loginEmptyUserAgent() {
        CookieLogin login = new CookieLogin(ZavaOptions.defaults());

        Credentials creds = Credentials.builder()
                .imei("imei")
                .cookies(List.of(new Credentials.CookieEntry("a", "b", "c")))
                .userAgent("")
                .build();

        assertThrows(ZavaAuthException.class, () -> login.login(creds));
    }
}
