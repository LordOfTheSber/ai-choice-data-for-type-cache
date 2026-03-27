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
        ConcurrentHashMap<String, CacheEntry> targetRegion = regions.get(value.getDataClass());
        CacheEntry candidate = new CacheEntry(value);

        CacheEntry selected = targetRegion.compute(key, (ignored, existing) -> {
            if (existing == null || value.getVersion() >= existing.getVersion()) {
                return candidate;
            }
            return existing;
        });

        if (selected == candidate) {
            removeFromOtherRegions(key, value.getDataClass(), value.getVersion());
            if (value.getStatus() == CacheStatus.HOT) {
                hotRegion.put(key, candidate);
            } else {
                hotRegion.computeIfPresent(key, (ignored, oldEntry) ->
                    oldEntry.getVersion() <= value.getVersion() ? candidate : oldEntry
                );
            }
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
        CacheEntry hotEntry = hotRegion.get(key);
        if (hotEntry != null && !hotEntry.isExpired()) {
            return true;
        }

        for (ConcurrentHashMap<String, CacheEntry> region : regions.values()) {
            CacheEntry entry = region.get(key);
            if (entry != null && !entry.isExpired()) {
                return true;
            }
        }
        return false;
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
            owningRegion.remove(key, entry);
            hotRegion.remove(key, entry);
            totalMisses.incrementAndGet();
            return Optional.empty();
        }

        entry.markHit();
        totalHits.incrementAndGet();
        return Optional.of(entry.getCacheValue().withStatus(CacheStatus.HIT));
    }

    private void promoteToHotRegionIfNeeded(String key, CacheEntry entry) {
        if (entry.getHitCount() >= HOT_PROMOTION_THRESHOLD) {
            hotRegion.compute(key, (ignored, existingHot) -> {
                if (existingHot == null || existingHot.getVersion() <= entry.getVersion()) {
                    return entry;
                }
                return existingHot;
            });
        }
    }

    private void removeFromOtherRegions(String key, DataClass targetClass, long minVersionToRemove) {
        for (Map.Entry<DataClass, ConcurrentHashMap<String, CacheEntry>> region : regions.entrySet()) {
            if (region.getKey() == targetClass) {
                continue;
            }
            region.getValue().computeIfPresent(key, (ignored, existing) ->
                existing.getVersion() <= minVersionToRemove ? null : existing
            );
        }
    }
}
