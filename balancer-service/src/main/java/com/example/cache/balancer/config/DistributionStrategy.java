package com.example.cache.balancer.config;

/**
 * Strategy for initial slot distribution across master nodes.
 */
public enum DistributionStrategy {

    /** Distribute slots evenly across all nodes regardless of weight. */
    ROUND_ROBIN,

    /** Distribute slots proportionally based on each node's declared weight. */
    WEIGHTED
}
