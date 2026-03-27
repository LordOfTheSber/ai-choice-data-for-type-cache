package com.example.cache.master.cache;

import com.example.cache.master.cache.config.CacheProperties;
import com.example.cache.master.cache.error.CacheErrorCode;
import com.example.cache.master.cache.error.CacheException;
import com.example.cache.master.cache.serialization.BinarySerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MasterCacheService {
    private static final Logger log = LoggerFactory.getLogger(MasterCacheService.class);

    private final InMemoryRegionCache l2Cache;
    @Nullable
    private final DiskRegionCache l3Cache;
    private final BinarySerializationService binarySerializationService;
    private final boolean l3Enabled;
    private final ConcurrentHashMap<String, Class<?>> typeCache;

    public MasterCacheService(InMemoryRegionCache l2Cache,
                              @Nullable DiskRegionCache l3Cache,
                              CacheProperties cacheProperties,
                              BinarySerializationService binarySerializationService) {
        this.l2Cache = l2Cache;
        this.l3Cache = l3Cache;
        this.binarySerializationService = binarySerializationService;
        this.l3Enabled = cacheProperties.getL3().isEnabled() && l3Cache != null;
        this.typeCache = new ConcurrentHashMap<>();
    }

    public Optional<CacheValue> get(String key) {
        validateKey(key);
        Optional<CacheEntry> l2Entry = l2Cache.get(key);
        if (l2Entry.isPresent()) {
            return Optional.of(toCacheHit(l2Entry.get()));
        }
        return fetchFromL3AndPromote(key).map(this::toCacheHit);
    }

    public void put(String key, CacheValue value) {
        validateKey(key);
        CacheEntry entry = toEntry(value);
        l2Cache.put(key, entry);
        if (shouldWriteToL3(entry)) {
            l3Cache.put(key, entry);
        }
    }

    public void delete(String key) {
        validateKey(key);
        l2Cache.delete(key);
        if (l3Enabled) {
            l3Cache.delete(key);
        }
    }

    public boolean contains(String key) {
        validateKey(key);
        if (l2Cache.contains(key)) {
            return true;
        }
        return l3Enabled && l3Cache.contains(key);
    }

    private CacheEntry toEntry(CacheValue value) {
        Object payloadValue = value.getValue();
        String typeName = payloadValue == null ? Object.class.getName() : payloadValue.getClass().getName();
        byte[] payload = binarySerializationService.serialize(payloadValue);
        return new CacheEntry(payload, typeName, value.getDataClass(), value.getTtl(), value.getStatus(), value.getVersion());
    }

    private CacheValue toCacheHit(CacheEntry entry) {
        Class<?> payloadType = resolveType(entry.getTypeName());
        Object value = binarySerializationService.deserialize(entry.getPayload(), payloadType);
        Duration ttl = Duration.ofMillis(entry.getTtlMillis());
        return new CacheValue(value, entry.getDataClass(), ttl, CacheStatus.HIT, entry.getVersion());
    }

    private Optional<CacheEntry> fetchFromL3AndPromote(String key) {
        if (!l3Enabled) {
            return Optional.empty();
        }
        Optional<CacheEntry> l3Entry = l3Cache.get(key);
        l3Entry.ifPresent(entry -> {
            l2Cache.put(key, entry);
            log.debug("Promoted key={} version={} from L3 to L2", key, entry.getVersion());
        });
        return l3Entry;
    }

    private Class<?> resolveType(String typeName) {
        return typeCache.computeIfAbsent(typeName, this::loadClass);
    }

    private Class<?> loadClass(String typeName) {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException exception) {
            throw new CacheException(CacheErrorCode.SERIALIZATION_ERROR, "Unknown cached type: " + typeName, exception);
        }
    }

    private boolean shouldWriteToL3(CacheEntry entry) {
        return l3Enabled && entry.getDataClass() == DataClass.IMMUTABLE;
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "key must not be blank");
        }
    }
}
