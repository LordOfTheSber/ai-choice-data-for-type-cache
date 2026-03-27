package com.example.cache.master.cache.api;

import com.example.cache.master.cache.DataClass;
import com.fasterxml.jackson.databind.JsonNode;

public record CacheTypedPutRequest(
    String typeName,
    JsonNode payload,
    DataClass dataClass,
    long ttlMillis,
    long version
) {
}
