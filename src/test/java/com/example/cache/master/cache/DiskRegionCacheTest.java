package com.example.cache.master.cache;

import com.example.cache.master.support.TestCachePropertiesFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DiskRegionCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void putAndGetShouldRoundTripEntry() {
        DiskRegionCache cache = cache();
        CacheEntry value = entry("book-content", 7, Duration.ofMinutes(10));

        cache.put("book:42", value);
        Optional<CacheEntry> restored = cache.get("book:42");

        assertTrue(restored.isPresent());
        assertEquals(7L, restored.get().getVersion());
        assertEquals(String.class.getName(), restored.get().getTypeName());
        assertArrayEquals("book-content".getBytes(), restored.get().getPayload());
    }

    @Test
    void putShouldIgnoreOlderVersion() {
        DiskRegionCache cache = cache();
        cache.put("k", entry("new", 5, Duration.ofMinutes(10)));
        cache.put("k", entry("old", 4, Duration.ofMinutes(10)));

        Optional<CacheEntry> restored = cache.get("k");

        assertTrue(restored.isPresent());
        assertEquals(5L, restored.get().getVersion());
        assertArrayEquals("new".getBytes(), restored.get().getPayload());
    }

    @Test
    void expiredValueShouldBeRemovedOnRead() throws InterruptedException {
        DiskRegionCache cache = cache();
        cache.put("ttl", entry("payload", 1, Duration.ofMillis(25)));

        Thread.sleep(60);

        assertTrue(cache.get("ttl").isEmpty());
        assertFalse(cache.contains("ttl"));
    }

    @Test
    void concurrentWriteShouldKeepHighestVersion() throws InterruptedException {
        DiskRegionCache cache = cache();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(30);

        for (int version = 1; version <= 30; version++) {
            final int finalVersion = version;
            executor.submit(() -> updateWithVersion(cache, finalVersion, latch));
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        CacheEntry result = cache.get("parallel").orElseThrow();
        assertEquals(30L, result.getVersion());
        assertArrayEquals("v30".getBytes(), result.getPayload());
    }

    @Test
    void deleteShouldRemoveValue() {
        DiskRegionCache cache = cache();
        cache.put("remove", entry("payload", 1, Duration.ofMinutes(1)));

        cache.delete("remove");

        assertFalse(cache.contains("remove"));
    }

    private void updateWithVersion(DiskRegionCache cache, int version, CountDownLatch latch) {
        try {
            cache.put("parallel", entry("v" + version, version, Duration.ofMinutes(5)));
        } finally {
            latch.countDown();
        }
    }

    private CacheEntry entry(String payload, long version, Duration ttl) {
        return new CacheEntry(payload.getBytes(), String.class.getName(), DataClass.IMMUTABLE, ttl, CacheStatus.COLD, version);
    }

    private DiskRegionCache cache() {
        return new DiskRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));
    }
}
