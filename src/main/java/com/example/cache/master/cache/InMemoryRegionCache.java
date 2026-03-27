package com.example.cache.master.cache;

import com.example.cache.master.cache.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryRegionCache implements CacheStore {
    private static final Logger log = LoggerFactory.getLogger(InMemoryRegionCache.class);

    private final Map<DataClass, ConcurrentHashMap<String, CacheEntry>> regions;
    private final ConcurrentHashMap<String, CacheEntry> hotRegion;
    private final AtomicLong totalHits;
    private final AtomicLong totalMisses;
    private final int hotPromotionThreshold;

    public InMemoryRegionCache(CacheProperties cacheProperties) {
        this.hotPromotionThreshold = cacheProperties.getHotRegion().getPromotionThreshold();
        this.regions = createRegions();
        this.hotRegion = new ConcurrentHashMap<>();
        this.totalHits = new AtomicLong(0L);
        this.totalMisses = new AtomicLong(0L);
    }

    @Override
    public Optional<CacheValue> get(String key) {
        Optional<CacheValue> hotValue = readFromRegion(key, hotRegion);
        if (hotValue.isPresent()) {
            return hotValue;
        }
        return readFromRegularRegions(key);
    }

    @Override
    public void put(String key, CacheValue value) {
        ConcurrentHashMap<String, CacheEntry> target = regions.get(value.getDataClass());
        CacheEntry candidate = new CacheEntry(value);
        CacheEntry selected = target.compute(key, (ignored, existing) -> selectNewest(candidate, existing));

        if (selected != candidate) {
            log.debug("Skip stale L2 update for key={} version={}", key, value.getVersion());
            return;
        }

        removeOldCopiesFromOtherRegions(key, value.getDataClass(), value.getVersion());
        updateHotRegion(key, candidate, value);
    }

    @Override
    public void delete(String key) {
        hotRegion.remove(key);
        regions.values().forEach(region -> region.remove(key));
    }

    @Override
    public boolean contains(String key) {
        if (isAlive(hotRegion.get(key))) {
            return true;
        }
        return regions.values().stream().map(region -> region.get(key)).anyMatch(this::isAlive);
    }

    public long getTotalHits() {
        return totalHits.get();
    }

    public long getTotalMisses() {
        return totalMisses.get();
    }

    private Map<DataClass, ConcurrentHashMap<String, CacheEntry>> createRegions() {
        Map<DataClass, ConcurrentHashMap<String, CacheEntry>> initialized = new EnumMap<>(DataClass.class);
        for (DataClass dataClass : DataClass.values()) {
            initialized.put(dataClass, new ConcurrentHashMap<>());
        }
        return initialized;
    }

    private Optional<CacheValue> readFromRegularRegions(String key) {
        for (ConcurrentHashMap<String, CacheEntry> region : regions.values()) {
            Optional<CacheValue> value = readFromRegion(key, region);
            if (value.isPresent()) {
                return value;
            }
        }
        totalMisses.incrementAndGet();
        return Optional.empty();
    }

    private Optional<CacheValue> readFromRegion(String key, ConcurrentHashMap<String, CacheEntry> region) {
        CacheEntry entry = region.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            return handleExpiredEntry(key, entry, region);
        }
        return handleAliveEntry(key, entry);
    }

    private Optional<CacheValue> handleExpiredEntry(String key,
                                                    CacheEntry entry,
                                                    ConcurrentHashMap<String, CacheEntry> region) {
        entry.markMiss();
        region.remove(key, entry);
        hotRegion.remove(key, entry);
        totalMisses.incrementAndGet();
        return Optional.empty();
    }

    private Optional<CacheValue> handleAliveEntry(String key, CacheEntry entry) {
        entry.markHit();
        totalHits.incrementAndGet();
        promoteToHotRegionIfNeeded(key, entry);
        return Optional.of(entry.getCacheValue().withStatus(CacheStatus.HIT));
    }

    private void promoteToHotRegionIfNeeded(String key, CacheEntry entry) {
        if (entry.getHitCount() < hotPromotionThreshold) {
            return;
        }
        hotRegion.compute(key, (ignored, existing) -> selectNewest(entry, existing));
    }

    private CacheEntry selectNewest(CacheEntry candidate, CacheEntry existing) {
        if (existing == null) {
            return candidate;
        }
        return candidate.getVersion() >= existing.getVersion() ? candidate : existing;
    }

    private void updateHotRegion(String key, CacheEntry candidate, CacheValue value) {
        if (value.getStatus() == CacheStatus.HOT) {
            hotRegion.compute(key, (ignored, existing) -> selectNewest(candidate, existing));
            return;
        }
        hotRegion.computeIfPresent(key, (ignored, existing) -> selectNewest(candidate, existing));
    }

    private void removeOldCopiesFromOtherRegions(String key, DataClass keepRegion, long minVersion) {
        for (Map.Entry<DataClass, ConcurrentHashMap<String, CacheEntry>> regionEntry : regions.entrySet()) {
            if (regionEntry.getKey() == keepRegion) {
                continue;
            }
            regionEntry.getValue().computeIfPresent(key, (ignored, existing) -> keepIfNewer(existing, minVersion));
        }
    }

    private CacheEntry keepIfNewer(CacheEntry existing, long minVersion) {
        return existing.getVersion() > minVersion ? existing : null;
    }

    private boolean isAlive(CacheEntry entry) {
        return entry != null && !entry.isExpired();
    }
}
