package com.example.cache.master.cluster;

import java.time.Instant;

public record MasterNodeView(
    String nodeId,
    MasterNodeStatus status,
    Instant lastHeartbeat,
    int weight
) {
}
