package com.example.cache.balancer.api;

import com.example.cache.balancer.registry.MasterNodeStatus;

import java.time.Instant;

public record NodeView(
    String nodeId,
    String baseUrl,
    int weight,
    MasterNodeStatus status,
    Instant lastHeartbeat,
    int assignedSlots
) {
}
