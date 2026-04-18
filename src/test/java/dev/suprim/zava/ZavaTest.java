package dev.suprim.zava;

import dev.suprim.zava.auth.Credentials;
import dev.suprim.zava.conversation.ThreadType;
import dev.suprim.zava.exception.ZavaAuthException;
import dev.suprim.zava.message.SendResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZavaTest {

    @Test
    void defaultOptions() {
        Zava zava = new Zava();
        assertNotNull(zava);
        assertNotNull(zava.getOptions());
    }

    @Test
    void customOptions() {
        ZavaOptions options = ZavaOptions.builder()
                .selfListen(true)
                .logging(false)
                .apiType(30)
                .apiVersion(671)
                .loginBaseUrl("http://localhost:9999")
                .build();

        assertTrue(options.isSelfListen());
        assertFalse(options.isLogging());
        assertEquals(30, options.getApiType());
        assertEquals(671, options.getApiVersion());
        assertNull(options.getProxy());
        assertEquals("http://localhost:9999", options.getLoginBaseUrl());
    }

    @Test
    void loginThrowsOnBadCredentials() {
        Zava zava = new Zava();
        Credentials creds = Credentials.builder()
                .imei("").cookies(List.of()).userAgent("UA").build();
        assertThrows(ZavaAuthException.class, () -> zava.login(creds));
    }

    @Test
    void threadTypeValues() {
        assertEquals(ThreadType.USER, ThreadType.valueOf("USER"));
        assertEquals(ThreadType.GROUP, ThreadType.valueOf("GROUP"));
        assertEquals(2, ThreadType.values().length);
    }

    @Test
    void sendResultGetters() {
        SendResult r = new SendResult(12345);
        assertEquals(12345, r.getMsgId());
        assertNotNull(r.toString());
        assertTrue(r.toString().contains("12345"));

        SendResult empty = new SendResult();
        assertEquals(0, empty.getMsgId());
    }
}
