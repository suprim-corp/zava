package dev.suprim.zava;

import dev.suprim.zava.internal.http.ZavaCookieJar;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.session.ServiceMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZavaClientTest {

    private ZavaClient client;

    @BeforeEach
    void setUp() {
        ServiceMap sm = new ServiceMap();
        for (String s : List.of("chat", "group", "group_poll", "friend", "profile",
                "reaction", "sticker", "label", "alias", "file", "conversation",
                "group_board", "auto_reply", "quick_message")) {
            sm.addService(s, List.of("https://fake.zalo.me"));
        }

        Context ctx = Context.builder()
                .uid("test-uid")
                .imei("test-imei")
                .secretKey(Base64.getEncoder().encodeToString("0123456789abcdef".getBytes()))
                .userAgent("UA")
                .language("vi")
                .options(ZavaOptions.defaults())
                .cookieJar(new ZavaCookieJar())
                .serviceMap(sm)
                .wsUrls(List.of("wss://fake.zalo.me/ws"))
                .build();

        client = new ZavaClient(ctx);
    }

    @Test @DisplayName("getUid returns uid")
    void getUid() { assertEquals("test-uid", client.getUid()); }

    @Test @DisplayName("messages() returns singleton")
    void messages() {
        assertNotNull(client.messages());
        assertSame(client.messages(), client.messages());
    }

    @Test @DisplayName("users() returns singleton")
    void users() {
        assertNotNull(client.users());
        assertSame(client.users(), client.users());
    }

    @Test @DisplayName("groups() returns singleton")
    void groups() {
        assertNotNull(client.groups());
        assertSame(client.groups(), client.groups());
    }

    @Test @DisplayName("reactions() returns singleton")
    void reactions() {
        assertNotNull(client.reactions());
        assertSame(client.reactions(), client.reactions());
    }

    @Test @DisplayName("stickers() returns singleton")
    void stickers() {
        assertNotNull(client.stickers());
        assertSame(client.stickers(), client.stickers());
    }

    @Test @DisplayName("polls() returns singleton")
    void polls() {
        assertNotNull(client.polls());
        assertSame(client.polls(), client.polls());
    }

    @Test @DisplayName("profile() returns singleton")
    void profile() {
        assertNotNull(client.profile());
        assertSame(client.profile(), client.profile());
    }

    @Test @DisplayName("settings() returns singleton")
    void settings() {
        assertNotNull(client.settings());
        assertSame(client.settings(), client.settings());
    }

    @Test @DisplayName("board() returns singleton")
    void board() {
        assertNotNull(client.board());
        assertSame(client.board(), client.board());
    }

    @Test @DisplayName("reminders() returns singleton")
    void reminders() {
        assertNotNull(client.reminders());
        assertSame(client.reminders(), client.reminders());
    }

    @Test @DisplayName("business() returns singleton")
    void business() {
        assertNotNull(client.business());
        assertSame(client.business(), client.business());
    }

    @Test @DisplayName("uploader() returns singleton")
    void uploader() {
        assertNotNull(client.uploader());
        assertSame(client.uploader(), client.uploader());
    }

    @Test @DisplayName("listener() returns singleton")
    void listener() {
        assertNotNull(client.listener());
        assertSame(client.listener(), client.listener());
    }

    @Test @DisplayName("getContext, getHttpClient, getResponseHandler not null")
    void internals() {
        assertNotNull(client.getContext());
        assertNotNull(client.getHttpClient());
        assertNotNull(client.getResponseHandler());
    }
}
