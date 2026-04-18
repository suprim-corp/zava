package dev.suprim.zava.internal.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceMapTest {

    @Test
    @DisplayName("getUrl returns first URL for a service")
    void getUrl() {
        ServiceMap map = new ServiceMap();
        map.addService("chat", Arrays.asList("https://chat1.zalo.me", "https://chat2.zalo.me"));

        assertEquals("https://chat1.zalo.me", map.getUrl("chat"));
    }

    @Test
    @DisplayName("getUrl throws on unknown service")
    void getUrlUnknown() {
        ServiceMap map = new ServiceMap();
        assertThrows(IllegalArgumentException.class, () -> map.getUrl("nonexistent"));
    }

    @Test
    @DisplayName("getUrls returns all URLs")
    void getUrls() {
        ServiceMap map = new ServiceMap();
        map.addService("group", Arrays.asList("https://g1.zalo.me", "https://g2.zalo.me"));

        List<String> urls = map.getUrls("group");
        assertEquals(2, urls.size());
        assertEquals("https://g1.zalo.me", urls.get(0));
    }

    @Test
    @DisplayName("getUrls returns empty list for unknown service")
    void getUrlsUnknown() {
        ServiceMap map = new ServiceMap();
        assertTrue(map.getUrls("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("hasService returns true for existing service")
    void hasService() {
        ServiceMap map = new ServiceMap();
        map.addService("file", Arrays.asList("https://file.zalo.me"));
        assertTrue(map.hasService("file"));
        assertFalse(map.hasService("other"));
    }

    @Test
    @DisplayName("deserializes from JSON via Jackson")
    void jacksonDeserialize() throws Exception {
        String json = "{\"chat\":[\"https://chat.zalo.me\"],\"group\":[\"https://g.zalo.me\",\"https://g2.zalo.me\"],\"unknown_future_field\":[\"url\"]}";
        ObjectMapper mapper = new ObjectMapper();
        ServiceMap map = mapper.readValue(json, ServiceMap.class);

        assertEquals("https://chat.zalo.me", map.getUrl("chat"));
        assertEquals(2, map.getUrls("group").size());
        assertTrue(map.hasService("unknown_future_field"));
    }
}
