package com.example.cache.master.cache;

import java.util.Optional;

public interface CacheStore {
    Optional<CacheEntry> get(String key);

    void put(String key, CacheEntry value);

    void delete(String key);

    boolean contains(String key);
}
