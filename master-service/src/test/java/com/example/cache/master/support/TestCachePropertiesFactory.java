package com.example.cache.master.support;

import com.example.cache.master.cache.config.CacheProperties;

public final class TestCachePropertiesFactory {
    private TestCachePropertiesFactory() {
    }

    public static CacheProperties withDiskPath(String path) {
        CacheProperties properties = new CacheProperties();
        properties.getL3().setPath(path);
        properties.getL3().setEnabled(true);
        properties.getL3().setLockStripes(16);
        properties.getHotRegion().setPromotionThreshold(5);
        return properties;
    }
}
