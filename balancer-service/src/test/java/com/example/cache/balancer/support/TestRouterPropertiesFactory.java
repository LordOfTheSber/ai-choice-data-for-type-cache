package com.example.cache.balancer.support;

import com.example.cache.balancer.config.DistributionStrategy;
import com.example.cache.balancer.config.RouterProperties;

public final class TestRouterPropertiesFactory {

    private TestRouterPropertiesFactory() {
    }

    public static RouterProperties withSlots(int slotCount) {
        return withSlots(slotCount, DistributionStrategy.WEIGHTED);
    }

    public static RouterProperties withSlots(int slotCount, DistributionStrategy strategy) {
        RouterProperties props = new RouterProperties();
        props.setSlotCount(slotCount);
        props.setDistributionStrategy(strategy);
        RouterProperties.RebalanceProperties rebalance = new RouterProperties.RebalanceProperties();
        rebalance.setEnabled(true);
        rebalance.setThresholdPercent(15);
        rebalance.setCooldownSeconds(0);
        props.setRebalance(rebalance);
        return props;
    }
}
