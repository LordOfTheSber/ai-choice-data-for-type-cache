package com.example.cache.balancer.registry;

import java.time.Instant;

/**
 * Immutable snapshot of a master node known to the router.
 */
public record MasterNode(
    String nodeId,
    String baseUrl,
    int weight,
    MasterNodeStatus status,
    Instant lastHeartbeat
) {

    public MasterNode withHeartbeat(Instant timestamp) {
        return new MasterNode(nodeId, baseUrl, weight, MasterNodeStatus.UP, timestamp);
    }

    public MasterNode withStatus(MasterNodeStatus newStatus) {
        return new MasterNode(nodeId, baseUrl, weight, newStatus, lastHeartbeat);
    }

    public MasterNode withWeight(int newWeight) {
        return new MasterNode(nodeId, baseUrl, newWeight, status, lastHeartbeat);
    }

    public boolean isUp() {
        return status == MasterNodeStatus.UP;
    }
}
