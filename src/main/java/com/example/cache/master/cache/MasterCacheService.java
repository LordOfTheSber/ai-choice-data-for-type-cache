package com.example.cache.master.cache;

import com.example.cache.master.cache.config.CacheProperties;
import com.example.cache.master.cache.error.CacheValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MasterCacheService {
    private static final Logger log = LoggerFactory.getLogger(MasterCacheService.class);

    private final InMemoryRegionCache l2Cache;
    @Nullable
    private final DiskRegionCache l3Cache;
    private final boolean l3Enabled;

    public MasterCacheService(InMemoryRegionCache l2Cache,
                              @Nullable DiskRegionCache l3Cache,
                              CacheProperties cacheProperties) {
        this.l2Cache = l2Cache;
        this.l3Cache = l3Cache;
        this.l3Enabled = cacheProperties.getL3().isEnabled() && l3Cache != null;
    }

    public Optional<CacheValue> get(String key) {
        validateKey(key);
        Optional<CacheValue> l2Value = l2Cache.get(key);
        if (l2Value.isPresent()) {
            return l2Value;
        }
        return fetchFromL3AndPromote(key);
    }

    public void put(String key, CacheValue value) {
        validateKey(key);
        l2Cache.put(key, value);
        if (shouldWriteToL3(value)) {
            l3Cache.put(key, value);
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

    private Optional<CacheValue> fetchFromL3AndPromote(String key) {
        if (!l3Enabled) {
            return Optional.empty();
        }
        Optional<CacheValue> l3Value = l3Cache.get(key);
        l3Value.ifPresent(value -> {
            l2Cache.put(key, value);
            log.debug("Promoted key={} version={} from L3 to L2", key, value.getVersion());
        });
        return l3Value;
    }

    private boolean shouldWriteToL3(CacheValue value) {
        return l3Enabled && value.getDataClass() == DataClass.IMMUTABLE;
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new CacheValidationException("key must not be blank");
        }
    }
}
