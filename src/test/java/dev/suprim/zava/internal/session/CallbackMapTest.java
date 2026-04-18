package dev.suprim.zava.internal.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CallbackMapTest {

    @Test
    @DisplayName("put and get returns the value")
    void putAndGet() {
        CallbackMap<String> map = new CallbackMap<>(60_000);
        try {
            map.put("key1", "value1");
            assertEquals("value1", map.get("key1"));
            assertEquals(1, map.size());
        } finally {
            map.shutdown();
        }
    }

    @Test
    @DisplayName("remove returns and deletes the value")
    void remove() {
        CallbackMap<String> map = new CallbackMap<>(60_000);
        try {
            map.put("key1", "value1");
            assertEquals("value1", map.remove("key1"));
            assertNull(map.get("key1"));
            assertEquals(0, map.size());
        } finally {
            map.shutdown();
        }
    }

    @Test
    @DisplayName("entry expires after TTL")
    void ttlExpiry() throws InterruptedException {
        CallbackMap<String> map = new CallbackMap<>(100); // 100ms TTL
        try {
            map.put("key1", "value1");
            assertNotNull(map.get("key1"));

            // Wait for TTL + buffer
            Thread.sleep(250);

            assertNull(map.get("key1"));
            assertEquals(0, map.size());
        } finally {
            map.shutdown();
        }
    }

    @Test
    @DisplayName("custom TTL per entry")
    void customTtl() throws InterruptedException {
        CallbackMap<String> map = new CallbackMap<>(60_000);
        try {
            map.put("short", "val", 100);  // 100ms
            map.put("long", "val", 60_000); // 60s

            Thread.sleep(250);

            assertNull(map.get("short"));
            assertNotNull(map.get("long"));
        } finally {
            map.shutdown();
        }
    }

    @Test
    @DisplayName("containsKey works correctly")
    void containsKey() {
        CallbackMap<String> map = new CallbackMap<>(60_000);
        try {
            map.put("key1", "value1");
            assertTrue(map.containsKey("key1"));
            assertFalse(map.containsKey("key2"));
        } finally {
            map.shutdown();
        }
    }
}
