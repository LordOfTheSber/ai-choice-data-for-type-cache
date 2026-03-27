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
    void putAndGetShouldRoundTripValueAsBytes() {
        DiskRegionCache cache = cache();
        CacheValue value = new CacheValue(
            "book-content",
            DataClass.IMMUTABLE,
            Duration.ofMinutes(10),
            CacheStatus.COLD,
            7
        );

        cache.put("book:42", value);
        Optional<CacheValue> restored = cache.get("book:42");

        assertTrue(restored.isPresent());
        assertEquals(7L, restored.get().getVersion());
        assertArrayEquals("book-content".getBytes(), (byte[]) restored.get().getValue());
    }

    @Test
    void putShouldIgnoreOlderVersion() {
        DiskRegionCache cache = cache();
        cache.put("k", new CacheValue("new", DataClass.IMMUTABLE, Duration.ofMinutes(10), CacheStatus.COLD, 5));
        cache.put("k", new CacheValue("old", DataClass.IMMUTABLE, Duration.ofMinutes(10), CacheStatus.COLD, 4));

        Optional<CacheValue> restored = cache.get("k");

        assertTrue(restored.isPresent());
        assertEquals(5L, restored.get().getVersion());
        assertArrayEquals("new".getBytes(), (byte[]) restored.get().getValue());
    }

    @Test
    void expiredValueShouldBeRemovedOnRead() throws InterruptedException {
        DiskRegionCache cache = cache();
        cache.put("ttl", new CacheValue("payload", DataClass.IMMUTABLE, Duration.ofMillis(25), CacheStatus.COLD, 1));

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

        CacheValue result = cache.get("parallel").orElseThrow();
        assertEquals(30L, result.getVersion());
        assertArrayEquals("v30".getBytes(), (byte[]) result.getValue());
    }

    @Test
    void deleteShouldRemoveValue() {
        DiskRegionCache cache = cache();
        cache.put("remove", new CacheValue("payload", DataClass.IMMUTABLE, Duration.ofMinutes(1), CacheStatus.COLD, 1));

        cache.delete("remove");

        assertFalse(cache.contains("remove"));
    }

    private void updateWithVersion(DiskRegionCache cache, int version, CountDownLatch latch) {
        try {
            CacheValue value = new CacheValue(
                "v" + version,
                DataClass.IMMUTABLE,
                Duration.ofMinutes(5),
                CacheStatus.COLD,
                version
            );
            cache.put("parallel", value);
        } finally {
            latch.countDown();
        }
    }

    private DiskRegionCache cache() {
        return new DiskRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));
    }
}
