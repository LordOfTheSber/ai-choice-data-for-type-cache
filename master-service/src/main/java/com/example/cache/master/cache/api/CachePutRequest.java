package com.example.cache.master.cache.api;

import com.example.cache.master.cache.DataClass;

public record CachePutRequest(
    String payload,
    DataClass dataClass,
    long ttlMillis,
    long version
) {
}
