package com.example.cache.master.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class CacheEntry {
    private final byte[] payload;
    private final String typeName;
    private final DataClass dataClass;
    private final long ttlMillis;
    private final CacheStatus status;
    private final long version;
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;
    private final AtomicLong hitCount;
    private final AtomicLong missCount;

    public CacheEntry(byte[] payload,
                      String typeName,
                      DataClass dataClass,
                      Duration ttl,
                      CacheStatus status,
                      long version) {
        this(payload, typeName, dataClass, ttl.toMillis(), status, version, Instant.now());
    }

    public CacheEntry(byte[] payload,
                      String typeName,
                      DataClass dataClass,
                      long ttlMillis,
                      CacheStatus status,
                      long version,
                      Instant createdAt) {
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.typeName = Objects.requireNonNull(typeName, "typeName must not be null");
        this.dataClass = Objects.requireNonNull(dataClass, "dataClass must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.ttlMillis = ttlMillis;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.lastAccessedAt = this.createdAt;
        this.hitCount = new AtomicLong(0L);
        this.missCount = new AtomicLong(0L);
    }

    public byte[] getPayload() {
        return payload;
    }

    public String getTypeName() {
        return typeName;
    }

    public DataClass getDataClass() {
        return dataClass;
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    public CacheStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
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
