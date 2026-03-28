package com.example.cache.balancer.slot;

import java.util.List;

/**
 * Immutable assignment of a single slot to a primary master and optional replicas.
 */
public record SlotAssignment(
    int slot,
    String primaryNodeId,
    List<String> replicaNodeIds
) {

    public SlotAssignment(int slot, String primaryNodeId) {
        this(slot, primaryNodeId, List.of());
    }
}
