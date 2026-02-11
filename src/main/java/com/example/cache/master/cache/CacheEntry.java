package com.example.cache.master.cache;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class CacheEntry {
    private final CacheValue cacheValue;
    private final long ttlMillis;
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;
    private final AtomicLong hitCount;
    private final AtomicLong missCount;

    public CacheEntry(CacheValue cacheValue) {
        this.cacheValue = Objects.requireNonNull(cacheValue, "cacheValue must not be null");
        this.ttlMillis = cacheValue.getTtl().toMillis();
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
        this.hitCount = new AtomicLong(0L);
        this.missCount = new AtomicLong(0L);
    }

    public CacheValue getCacheValue() {
        return cacheValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public DataClass getDataClass() {
        return cacheValue.getDataClass();
    }

    public boolean isExpired() {
        if (ttlMillis <= 0) {
            return false;
        }
        long ageMillis = Instant.now().toEpochMilli() - createdAt.toEpochMilli();
        return ageMillis >= ttlMillis;
    }

    public void markHit() {
        hitCount.incrementAndGet();
        lastAccessedAt = Instant.now();
    }

    public void markMiss() {
        missCount.incrementAndGet();
    }
}
