package com.example.cache.master.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DiskRegionCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void putAndGetShouldRoundTripValueAsBytes() {
        DiskRegionCache cache = new DiskRegionCache(tempDir.toString());
        CacheValue value = new CacheValue("book-content", DataClass.IMMUTABLE, Duration.ofMinutes(10), CacheStatus.COLD, 7);

        cache.put("book:42", value);
        Optional<CacheValue> restored = cache.get("book:42");

        assertTrue(restored.isPresent());
        assertEquals(7L, restored.get().getVersion());
        assertEquals(DataClass.IMMUTABLE, restored.get().getDataClass());
        assertEquals(CacheStatus.HIT, restored.get().getStatus());
        assertArrayEquals("book-content".getBytes(), (byte[]) restored.get().getValue());
    }

    @Test
    void putShouldIgnoreOlderVersion() {
        DiskRegionCache cache = new DiskRegionCache(tempDir.toString());
        cache.put("k", new CacheValue("new", DataClass.IMMUTABLE, Duration.ofMinutes(10), CacheStatus.COLD, 5));
        cache.put("k", new CacheValue("old", DataClass.IMMUTABLE, Duration.ofMinutes(10), CacheStatus.COLD, 4));

        Optional<CacheValue> restored = cache.get("k");

        assertTrue(restored.isPresent());
        assertEquals(5L, restored.get().getVersion());
        assertArrayEquals("new".getBytes(), (byte[]) restored.get().getValue());
    }

    @Test
    void expiredValueShouldBeRemovedOnRead() throws InterruptedException {
        DiskRegionCache cache = new DiskRegionCache(tempDir.toString());
        cache.put("ttl", new CacheValue("payload", DataClass.IMMUTABLE, Duration.ofMillis(25), CacheStatus.COLD, 1));

        Thread.sleep(60);

        assertTrue(cache.get("ttl").isEmpty());
        assertFalse(cache.contains("ttl"));
    }

    @Test
    void deleteShouldRemoveValue() {
        DiskRegionCache cache = new DiskRegionCache(tempDir.toString());
        cache.put("remove", new CacheValue("payload", DataClass.IMMUTABLE, Duration.ofMinutes(1), CacheStatus.COLD, 1));

        cache.delete("remove");

        assertFalse(cache.contains("remove"));
    }
}
