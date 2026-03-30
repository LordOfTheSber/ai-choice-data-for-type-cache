package com.example.cache.master.cache.api;

import com.example.cache.master.cache.CacheStatus;
import com.example.cache.master.cache.DataClass;

public record CacheGetResponse(
    String key,
    String payload,
    DataClass dataClass,
    long ttlMillis,
    long version,
    CacheStatus status
) {
}
