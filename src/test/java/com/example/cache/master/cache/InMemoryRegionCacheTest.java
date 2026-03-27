package com.example.cache.master.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRegionCacheTest {

    private final InMemoryRegionCache cache = new InMemoryRegionCache();

    @Test
    void getShouldReturnEmptyForUnknownKey() {
        Optional<CacheValue> value = cache.get("unknown");

        assertTrue(value.isEmpty());
        assertEquals(0L, cache.getTotalHits());
        assertEquals(1L, cache.getTotalMisses());
    }

    @Test
    void putAndGetShouldWorkForRegion() {
        CacheValue value = value("book:1", DataClass.IMMUTABLE, 5);

        cache.put("book:1", value);
        Optional<CacheValue> restored = cache.get("book:1");

        assertTrue(restored.isPresent());
        assertEquals(5L, restored.get().getVersion());
        assertEquals(DataClass.IMMUTABLE, restored.get().getDataClass());
        assertEquals(CacheStatus.HIT, restored.get().getStatus());
    }

    @Test
    void putShouldNotOverrideNewerVersionWithOlderVersion() {
        cache.put("k1", value("new", DataClass.MEDIUM, 10));
        cache.put("k1", value("old", DataClass.MEDIUM, 1));

        Optional<CacheValue> actual = cache.get("k1");

        assertTrue(actual.isPresent());
        assertEquals(10L, actual.get().getVersion());
        assertEquals("new", actual.get().getValue());
    }

    @Test
    void putWithNewerVersionShouldMoveKeyAcrossRegions() {
        cache.put("k2", value("v1", DataClass.MEDIUM, 2));
        cache.put("k2", value("v2", DataClass.DYNAMIC, 3));

        Optional<CacheValue> actual = cache.get("k2");

        assertTrue(actual.isPresent());
        assertEquals(DataClass.DYNAMIC, actual.get().getDataClass());
        assertEquals(3L, actual.get().getVersion());
    }

    @Test
    void entryShouldExpireByTtl() throws InterruptedException {
        cache.put("short", new CacheValue("v", DataClass.DYNAMIC, Duration.ofMillis(30), CacheStatus.COLD, 1));

        Thread.sleep(60);

        assertTrue(cache.get("short").isEmpty());
        assertFalse(cache.contains("short"));
    }

    @Test
    void entryShouldBePromotedToHotRegionAfterHitsThreshold() {
        cache.put("hot-key", value("payload", DataClass.IMMUTABLE, 1));

        for (int i = 0; i < 6; i++) {
            assertTrue(cache.get("hot-key").isPresent());
        }

        assertTrue(cache.contains("hot-key"));
        assertTrue(cache.getTotalHits() >= 6);
    }

    private CacheValue value(String payload, DataClass dataClass, long version) {
        return new CacheValue(payload, dataClass, Duration.ofMinutes(5), CacheStatus.COLD, version);
    }
}
