package com.example.cache.balancer.slot;

import com.example.cache.balancer.config.RouterProperties;
import org.springframework.stereotype.Component;

/**
 * Computes the target slot for a given cache key using a stable hash.
 * Uses FNV-1a 32-bit to avoid the poor distribution of {@link String#hashCode()}
 * on short or similar keys.
 */
@Component
public class SlotCalculator {

    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    private final int slotCount;

    public SlotCalculator(RouterProperties routerProperties) {
        this.slotCount = routerProperties.getSlotCount();
    }

    public int slotFor(String key) {
        int hash = fnv1aHash(key);
        return Math.floorMod(hash, slotCount);
    }

    public int getSlotCount() {
        return slotCount;
    }

    private int fnv1aHash(String key) {
        int hash = FNV_OFFSET_BASIS;
        for (int idx = 0; idx < key.length(); idx++) {
            hash ^= key.charAt(idx);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
