package dev.suprim.zava.internal.http;

import dev.suprim.zava.ZavaOptions;
import dev.suprim.zava.internal.session.Context;
import dev.suprim.zava.internal.session.ServiceMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UrlsTest {

    private Context context;

    @BeforeEach
    void setUp() {
        ServiceMap serviceMap = new ServiceMap();
        serviceMap.addService("chat", Arrays.asList("https://chat.zalo.me"));
        serviceMap.addService("group", Arrays.asList("https://group.zalo.me"));

        context = Context.builder()
                .options(ZavaOptions.defaults())
                .serviceMap(serviceMap)
                .cookieJar(new ZavaCookieJar())
                .build();
    }

    @Test
    @DisplayName("build appends zpw_ver and zpw_type by default")
    void buildWithApiVersion() {
        String url = Urls.build("https://chat.zalo.me/api/send", context);
        assertTrue(url.contains("zpw_ver=671"));
        assertTrue(url.contains("zpw_type=30"));
    }

    @Test
    @DisplayName("build with params includes them in URL")
    void buildWithParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("params", "encrypted_data");
        params.put("nretry", 0);

        String url = Urls.build("https://chat.zalo.me/api/send", params, context);
        assertTrue(url.contains("params=encrypted_data"));
        assertTrue(url.contains("nretry=0"));
        assertTrue(url.contains("zpw_ver=671"));
    }

    @Test
    @DisplayName("build without apiVersion omits zpw_ver and zpw_type")
    void buildWithoutApiVersion() {
        String url = Urls.build("https://chat.zalo.me/api/send", Map.of(), false, context);
        assertFalse(url.contains("zpw_ver"));
        assertFalse(url.contains("zpw_type"));
    }

    @Test
    @DisplayName("build handles URL that already has query params")
    void buildExistingQueryParams() {
        String url = Urls.build("https://chat.zalo.me/api?existing=1", Map.of("new", "2"), context);
        assertTrue(url.contains("existing=1"));
        assertTrue(url.contains("new=2"));
        assertTrue(url.contains("&")); // should use & not ?
    }

    @Test
    @DisplayName("service builds URL from service map")
    void service() {
        String url = Urls.service(context, "chat", "/api/message/send");
        assertEquals("https://chat.zalo.me/api/message/send?zpw_ver=671&zpw_type=30", url);
    }

    @Test
    @DisplayName("service with params")
    void serviceWithParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("params", "data");

        String url = Urls.service(context, "group", "/api/group/create", params);
        assertTrue(url.startsWith("https://group.zalo.me/api/group/create?"));
        assertTrue(url.contains("params=data"));
        assertTrue(url.contains("zpw_ver=671"));
    }

    @Test
    @DisplayName("build skips null param values")
    void buildSkipsNull() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("a", "val");
        params.put("b", null);

        String url = Urls.build("https://x.com/api", params, false, context);
        assertTrue(url.contains("a=val"));
        assertFalse(url.contains("b="));
    }
}
