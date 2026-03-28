package com.example.cache.balancer.routing;

import java.util.List;

public record RoutingInfo(
    String key,
    int slot,
    String primaryNodeId,
    List<String> replicaNodeIds
) {
}
