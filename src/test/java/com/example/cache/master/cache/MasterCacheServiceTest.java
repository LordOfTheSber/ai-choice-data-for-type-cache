package com.example.cache.master.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MasterCacheServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void getShouldFallbackToL3AndPromoteToL2() {
        InMemoryRegionCache l2 = new InMemoryRegionCache();
        DiskRegionCache l3 = new DiskRegionCache(tempDir.toString());
        MasterCacheService service = new MasterCacheService(l2, l3, true);

        CacheValue value = new CacheValue("from-l3", DataClass.IMMUTABLE, Duration.ofMinutes(5), CacheStatus.COLD, 10);
        l3.put("k", value);

        Optional<CacheValue> firstRead = service.get("k");
        assertTrue(firstRead.isPresent());
        assertEquals(10L, firstRead.get().getVersion());

        l3.delete("k");

        Optional<CacheValue> secondRead = service.get("k");
        assertTrue(secondRead.isPresent(), "value should be promoted to L2");
        assertEquals(10L, secondRead.get().getVersion());
    }

    @Test
    void putShouldWriteOnlyImmutableClassToL3() {
        InMemoryRegionCache l2 = new InMemoryRegionCache();
        DiskRegionCache l3 = new DiskRegionCache(tempDir.toString());
        MasterCacheService service = new MasterCacheService(l2, l3, true);

        service.put("immutable", new CacheValue("A", DataClass.IMMUTABLE, Duration.ofMinutes(5), CacheStatus.COLD, 1));
        service.put("medium", new CacheValue("B", DataClass.MEDIUM, Duration.ofMinutes(5), CacheStatus.COLD, 1));

        assertTrue(l3.contains("immutable"));
        assertFalse(l3.contains("medium"));
    }

    @Test
    void serviceShouldWorkWhenL3Disabled() {
        InMemoryRegionCache l2 = new InMemoryRegionCache();
        DiskRegionCache l3 = new DiskRegionCache(tempDir.toString());
        MasterCacheService service = new MasterCacheService(l2, l3, false);

        service.put("key", new CacheValue("v", DataClass.IMMUTABLE, Duration.ofMinutes(2), CacheStatus.COLD, 1));

        assertTrue(service.contains("key"));
        assertFalse(l3.contains("key"), "L3 must not be used when disabled");
    }

    @Test
    void deleteShouldRemoveFromBothLevels() {
        InMemoryRegionCache l2 = new InMemoryRegionCache();
        DiskRegionCache l3 = new DiskRegionCache(tempDir.toString());
        MasterCacheService service = new MasterCacheService(l2, l3, true);

        service.put("key", new CacheValue("value", DataClass.IMMUTABLE, Duration.ofMinutes(2), CacheStatus.COLD, 1));
        assertTrue(service.contains("key"));

        service.delete("key");

        assertFalse(service.contains("key"));
        assertFalse(l3.contains("key"));
    }
}
