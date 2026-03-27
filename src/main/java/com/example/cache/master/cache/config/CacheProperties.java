package com.example.cache.master.cache.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {
    private final HotRegion hotRegion = new HotRegion();
    private final L3 l3 = new L3();

    public HotRegion getHotRegion() {
        return hotRegion;
    }

    public L3 getL3() {
        return l3;
    }

    public static class HotRegion {
        @Min(1)
        private int promotionThreshold = 5;

        public int getPromotionThreshold() {
            return promotionThreshold;
        }

        public void setPromotionThreshold(int promotionThreshold) {
            this.promotionThreshold = promotionThreshold;
        }
    }

    public static class L3 {
        private boolean enabled = true;

        @NotBlank
        private String path = "./data/l3-cache";

        @Min(1)
        private int lockStripes = 64;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getLockStripes() {
            return lockStripes;
        }

        public void setLockStripes(int lockStripes) {
            this.lockStripes = lockStripes;
        }
    }
}
