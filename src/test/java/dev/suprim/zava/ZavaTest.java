package dev.suprim.zava;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZavaTest {

    @Test
    void defaultOptions() {
        Zava zava = new Zava();
        assertNotNull(zava);
    }

    @Test
    void customOptions() {
        ZavaOptions options = ZavaOptions.builder()
                .selfListen(true)
                .logging(false)
                .apiType(30)
                .apiVersion(671)
                .build();

        assertEquals(true, options.isSelfListen());
        assertEquals(false, options.isLogging());
        assertEquals(30, options.getApiType());
        assertEquals(671, options.getApiVersion());
        assertNull(options.getProxy());
    }
}
