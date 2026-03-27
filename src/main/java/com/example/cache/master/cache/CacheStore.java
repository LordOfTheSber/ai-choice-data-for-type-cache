package com.example.cache.master.cache;

import java.util.Optional;

public interface CacheStore {
    Optional<CacheValue> get(String key);

    void put(String key, CacheValue value);

    void delete(String key);

    boolean contains(String key);
}
