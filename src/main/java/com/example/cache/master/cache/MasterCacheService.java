package com.example.cache.master.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MasterCacheService {

    private final InMemoryRegionCache l2Cache;
    @Nullable
    private final DiskRegionCache l3Cache;
    private final boolean l3Enabled;

    public MasterCacheService(InMemoryRegionCache l2Cache,
                              @Nullable DiskRegionCache l3Cache,
                              @Value("${cache.l3.enabled:true}") boolean l3Enabled) {
        this.l2Cache = l2Cache;
        this.l3Cache = l3Cache;
        this.l3Enabled = l3Enabled && l3Cache != null;
    }

    public Optional<CacheValue> get(String key) {
        Optional<CacheValue> l2Value = l2Cache.get(key);
        if (l2Value.isPresent()) {
            return l2Value;
        }

        if (!l3Enabled) {
            return Optional.empty();
        }

        Optional<CacheValue> l3Value = l3Cache.get(key);
        l3Value.ifPresent(value -> l2Cache.put(key, value.withStatus(CacheStatus.HIT)));
        return l3Value;
    }

    public void put(String key, CacheValue value) {
        l2Cache.put(key, value);

        if (l3Enabled && shouldStoreInL3(value.getDataClass())) {
            l3Cache.put(key, value);
        }
    }

    public void delete(String key) {
        l2Cache.delete(key);
        if (l3Enabled) {
            l3Cache.delete(key);
        }
    }

    public boolean contains(String key) {
        if (l2Cache.contains(key)) {
            return true;
        }

        return l3Enabled && l3Cache.contains(key);
    }

    private boolean shouldStoreInL3(DataClass dataClass) {
        return dataClass == DataClass.IMMUTABLE;
    }
}
