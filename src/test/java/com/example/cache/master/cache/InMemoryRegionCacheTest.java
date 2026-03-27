package com.example.cache.master.cache;

import com.example.cache.master.support.TestCachePropertiesFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRegionCacheTest {

    @Test
    void getShouldReturnEmptyForUnknownKey() {
        InMemoryRegionCache cache = cache();

        Optional<CacheValue> value = cache.get("unknown");

        assertTrue(value.isEmpty());
        assertEquals(0L, cache.getTotalHits());
        assertEquals(1L, cache.getTotalMisses());
    }

    @Test
    void putShouldNotOverrideNewerVersionWithOlderVersion() {
        InMemoryRegionCache cache = cache();

        cache.put("k1", value("new", DataClass.MEDIUM, 10));
        cache.put("k1", value("old", DataClass.MEDIUM, 1));

        Optional<CacheValue> actual = cache.get("k1");

        assertTrue(actual.isPresent());
        assertEquals(10L, actual.get().getVersion());
        assertEquals("new", actual.get().getValue());
    }

    @Test
    void keyShouldMoveAcrossRegionsWhenNewerVersionArrives() {
        InMemoryRegionCache cache = cache();

        cache.put("k2", value("v1", DataClass.MEDIUM, 2));
        cache.put("k2", value("v2", DataClass.DYNAMIC, 3));

        Optional<CacheValue> actual = cache.get("k2");

        assertTrue(actual.isPresent());
        assertEquals(DataClass.DYNAMIC, actual.get().getDataClass());
        assertEquals(3L, actual.get().getVersion());
    }

    @Test
    void entryShouldExpireByTtl() throws InterruptedException {
        InMemoryRegionCache cache = cache();
        cache.put("short", new CacheValue("v", DataClass.DYNAMIC, Duration.ofMillis(30), CacheStatus.COLD, 1));

        Thread.sleep(60);

        assertTrue(cache.get("short").isEmpty());
        assertFalse(cache.contains("short"));
    }

    @Test
    void burstReadShouldPromoteHotKeyAndKeepHitStats() {
        InMemoryRegionCache cache = cache();
        cache.put("hot-key", value("payload", DataClass.IMMUTABLE, 1));

        for (int iteration = 0; iteration < 6; iteration++) {
            assertTrue(cache.get("hot-key").isPresent());
        }

        assertTrue(cache.contains("hot-key"));
        assertTrue(cache.getTotalHits() >= 6);
    }

    @Test
    void concurrentPutShouldKeepLatestVersion() throws InterruptedException {
        InMemoryRegionCache cache = cache();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(40);

        for (int version = 1; version <= 40; version++) {
            final int finalVersion = version;
            executor.submit(() -> updateWithVersion(cache, finalVersion, latch));
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        CacheValue actual = cache.get("parallel").orElseThrow();
        assertEquals(40L, actual.getVersion());
        assertEquals("v40", actual.getValue());
    }

    private void updateWithVersion(InMemoryRegionCache cache, int version, CountDownLatch latch) {
        try {
            cache.put("parallel", value("v" + version, DataClass.IMMUTABLE, version));
        } finally {
            latch.countDown();
        }
    }

    private InMemoryRegionCache cache() {
        return new InMemoryRegionCache(TestCachePropertiesFactory.withDiskPath("./build/test-l3"));
    }

    private CacheValue value(String payload, DataClass dataClass, long version) {
        return new CacheValue(payload, dataClass, Duration.ofMinutes(5), CacheStatus.COLD, version);
    }
}
