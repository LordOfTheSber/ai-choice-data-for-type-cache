package com.example.cache.master.cache;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryRegionCache implements CacheStore {
    private static final long HOT_PROMOTION_THRESHOLD = 5L;

    private final Map<DataClass, ConcurrentHashMap<String, CacheEntry>> regions;
    private final ConcurrentHashMap<String, CacheEntry> hotRegion;
    private final AtomicLong totalHits;
    private final AtomicLong totalMisses;

    public InMemoryRegionCache() {
        this.regions = new EnumMap<>(DataClass.class);
        for (DataClass dataClass : DataClass.values()) {
            this.regions.put(dataClass, new ConcurrentHashMap<>());
        }
        this.hotRegion = new ConcurrentHashMap<>();
        this.totalHits = new AtomicLong(0L);
        this.totalMisses = new AtomicLong(0L);
    }

    @Override
    public Optional<CacheValue> get(String key) {
        CacheEntry hotEntry = hotRegion.get(key);
        if (hotEntry != null) {
            return toValueIfAlive(key, hotEntry, hotRegion);
        }

        for (ConcurrentHashMap<String, CacheEntry> region : regions.values()) {
            CacheEntry entry = region.get(key);
            if (entry != null) {
                Optional<CacheValue> found = toValueIfAlive(key, entry, region);
                found.ifPresent(cacheValue -> promoteToHotRegionIfNeeded(key, entry));
                return found;
            }
        }

        totalMisses.incrementAndGet();
        return Optional.empty();
    }

    @Override
    public void put(String key, CacheValue value) {
        CacheEntry entry = new CacheEntry(value);
        regions.get(value.getDataClass()).put(key, entry);

        if (value.getStatus() == CacheStatus.HOT) {
            hotRegion.put(key, entry);
        }
    }

    @Override
    public void delete(String key) {
        hotRegion.remove(key);
        for (ConcurrentHashMap<String, CacheEntry> region : regions.values()) {
            region.remove(key);
        }
    }

    @Override
    public boolean contains(String key) {
        return get(key).isPresent();
    }

    public long getTotalHits() {
        return totalHits.get();
    }

    public long getTotalMisses() {
        return totalMisses.get();
    }

    private Optional<CacheValue> toValueIfAlive(String key,
                                                CacheEntry entry,
                                                ConcurrentHashMap<String, CacheEntry> owningRegion) {
        if (entry.isExpired()) {
            entry.markMiss();
            owningRegion.remove(key);
            hotRegion.remove(key);
            totalMisses.incrementAndGet();
            return Optional.empty();
        }

        entry.markHit();
        totalHits.incrementAndGet();
        return Optional.of(entry.getCacheValue().withStatus(CacheStatus.HIT));
    }

    private void promoteToHotRegionIfNeeded(String key, CacheEntry entry) {
        if (entry.getHitCount() >= HOT_PROMOTION_THRESHOLD) {
            hotRegion.put(key, entry);
        }
    }
}
