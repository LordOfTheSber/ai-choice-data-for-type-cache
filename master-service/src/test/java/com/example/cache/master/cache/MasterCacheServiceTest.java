package com.example.cache.master.cache;

import com.example.cache.master.cache.config.SerializationProperties;
import com.example.cache.master.cache.serialization.BinarySerializationService;
import com.example.cache.master.cache.serialization.KryoPayloadSerializer;
import com.example.cache.master.cache.config.KryoConfig;
import com.example.cache.master.support.TestCachePropertiesFactory;
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
        InMemoryRegionCache l2 = new InMemoryRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));
        DiskRegionCache l3 = new DiskRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));
        MasterCacheService service = service(l2, l3);

        CacheValue value = new CacheValue("from-l3", DataClass.IMMUTABLE, Duration.ofMinutes(5), CacheStatus.COLD, 10);
        byte[] payload = binaryService().serialize(value.getValue());
        CacheEntry entry = new CacheEntry(
            payload,
            String.class.getName(),
            value.getDataClass(),
            value.getTtl(),
            value.getStatus(),
            value.getVersion()
        );
        l3.put("k", entry);

        Optional<CacheValue> firstRead = service.get("k");
        assertTrue(firstRead.isPresent());

        l3.delete("k");

        Optional<CacheValue> secondRead = service.get("k");
        assertTrue(secondRead.isPresent());
        assertEquals(10L, secondRead.get().getVersion());
        assertEquals("from-l3", secondRead.get().getValue());
    }

    @Test
    void putShouldWriteOnlyImmutableClassToL3() {
        InMemoryRegionCache l2 = new InMemoryRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));
        DiskRegionCache l3 = new DiskRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));
        MasterCacheService service = service(l2, l3);

        service.put("immutable", new CacheValue("A", DataClass.IMMUTABLE, Duration.ofMinutes(5), CacheStatus.COLD, 1));
        service.put("medium", new CacheValue("B", DataClass.MEDIUM, Duration.ofMinutes(5), CacheStatus.COLD, 1));

        assertTrue(l3.contains("immutable"));
        assertFalse(l3.contains("medium"));
    }

    @Test
    void serviceShouldWorkWhenL3Disabled() {
        InMemoryRegionCache l2 = new InMemoryRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));
        DiskRegionCache l3 = new DiskRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));

        var properties = TestCachePropertiesFactory.withDiskPath(tempDir.toString());
        properties.getL3().setEnabled(false);

        MasterCacheService service = new MasterCacheService(l2, l3, properties, binaryService());
        service.put("key", new CacheValue("v", DataClass.IMMUTABLE, Duration.ofMinutes(2), CacheStatus.COLD, 1));

        assertTrue(service.contains("key"));
        assertFalse(l3.contains("key"));
    }

    @Test
    void deleteShouldRemoveFromBothLevels() {
        InMemoryRegionCache l2 = new InMemoryRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));
        DiskRegionCache l3 = new DiskRegionCache(TestCachePropertiesFactory.withDiskPath(tempDir.toString()));
        MasterCacheService service = service(l2, l3);

        service.put("key", new CacheValue("value", DataClass.IMMUTABLE, Duration.ofMinutes(2), CacheStatus.COLD, 1));
        service.delete("key");

        assertFalse(service.contains("key"));
        assertFalse(l3.contains("key"));
    }

    private MasterCacheService service(InMemoryRegionCache l2, DiskRegionCache l3) {
        return new MasterCacheService(l2, l3, TestCachePropertiesFactory.withDiskPath(tempDir.toString()), binaryService());
    }

    private BinarySerializationService binaryService() {
        SerializationProperties properties = new SerializationProperties();
        KryoPayloadSerializer kryo = new KryoPayloadSerializer(new KryoConfig().kryoFactory(), properties);
        return new BinarySerializationService(properties, kryo);
    }
}
