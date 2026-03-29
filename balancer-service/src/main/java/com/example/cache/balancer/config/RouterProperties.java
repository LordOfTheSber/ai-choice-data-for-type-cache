package com.example.cache.balancer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "router")
public class RouterProperties {

    private int slotCount = 16384;
    private DistributionStrategy distributionStrategy = DistributionStrategy.WEIGHTED;
    private RebalanceProperties rebalance = new RebalanceProperties();

    public int getSlotCount() {
        return slotCount;
    }

    public void setSlotCount(int slotCount) {
        this.slotCount = slotCount;
    }

    public DistributionStrategy getDistributionStrategy() {
        return distributionStrategy;
    }

    public void setDistributionStrategy(DistributionStrategy distributionStrategy) {
        this.distributionStrategy = distributionStrategy;
    }

    public RebalanceProperties getRebalance() {
        return rebalance;
    }

    public void setRebalance(RebalanceProperties rebalance) {
        this.rebalance = rebalance;
    }

    public static class RebalanceProperties {

        private boolean enabled = true;
        private int thresholdPercent = 15;
        private long cooldownSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getThresholdPercent() {
            return thresholdPercent;
        }

        public void setThresholdPercent(int thresholdPercent) {
            this.thresholdPercent = thresholdPercent;
        }

        public long getCooldownSeconds() {
            return cooldownSeconds;
        }

        public void setCooldownSeconds(long cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }
    }
}
