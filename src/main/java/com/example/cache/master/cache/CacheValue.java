package com.example.cache.master.cache;

import java.time.Duration;
import java.util.Objects;

public final class CacheValue {
    private final Object value;
    private final DataClass dataClass;
    private final Duration ttl;
    private final CacheStatus status;

    public CacheValue(Object value, DataClass dataClass, Duration ttl, CacheStatus status) {
        this.value = value;
        this.dataClass = Objects.requireNonNull(dataClass, "dataClass must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public Object getValue() {
        return value;
    }

    public DataClass getDataClass() {
        return dataClass;
    }

    public Duration getTtl() {
        return ttl;
    }

    public CacheStatus getStatus() {
        return status;
    }

    public CacheValue withStatus(CacheStatus newStatus) {
        return new CacheValue(this.value, this.dataClass, this.ttl, newStatus);
    }
}
