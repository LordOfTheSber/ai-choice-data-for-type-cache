package com.example.cache.balancer.routing;

import com.example.cache.balancer.client.MasterHttpClient;
import com.example.cache.balancer.client.MasterResponse;
import com.example.cache.balancer.error.RouterErrorCode;
import com.example.cache.balancer.error.RouterException;
import com.example.cache.balancer.registry.MasterNode;
import com.example.cache.balancer.registry.NodeRegistry;
import com.example.cache.balancer.slot.SlotCalculator;
import com.example.cache.balancer.slot.SlotAssignment;
import com.example.cache.balancer.slot.SlotTable;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Core routing logic: resolves a cache key to its owning master node and delegates the operation.
 */
@Service
public class CacheRouter {

    private static final Logger log = LoggerFactory.getLogger(CacheRouter.class);

    private final SlotCalculator slotCalculator;
    private final SlotTable slotTable;
    private final NodeRegistry nodeRegistry;
    private final MasterHttpClient masterHttpClient;
    private final Counter routeCounter;
    private final Counter fallbackCounter;

    public CacheRouter(SlotCalculator slotCalculator,
                       SlotTable slotTable,
                       NodeRegistry nodeRegistry,
                       MasterHttpClient masterHttpClient,
                       MeterRegistry meterRegistry) {
        this.slotCalculator = slotCalculator;
        this.slotTable = slotTable;
        this.nodeRegistry = nodeRegistry;
        this.masterHttpClient = masterHttpClient;
        this.routeCounter = meterRegistry.counter("router.route.total");
        this.fallbackCounter = meterRegistry.counter("router.route.fallback");
    }

    public MasterResponse routeGet(String key) {
        routeCounter.increment();
        MasterNode node = resolveNode(key);
        return masterHttpClient.get(node, key);
    }

    public MasterResponse routePut(String key, String payload) {
        routeCounter.increment();
        MasterNode node = resolveNode(key);
        return masterHttpClient.put(node, key, payload);
    }

    public MasterResponse routeDelete(String key) {
        routeCounter.increment();
        MasterNode node = resolveNode(key);
        return masterHttpClient.delete(node, key);
    }

    public RoutingInfo resolveRoutingInfo(String key) {
        int slot = slotCalculator.slotFor(key);
        SlotAssignment assignment = slotTable.lookup(slot).orElse(null);
        String primaryNodeId = assignment != null ? assignment.primaryNodeId() : null;
        List<String> replicaNodeIds = assignment != null ? assignment.replicaNodeIds() : List.of();
        return new RoutingInfo(key, slot, primaryNodeId, replicaNodeIds);
    }

    private MasterNode resolveNode(String key) {
        int slot = slotCalculator.slotFor(key);
        SlotAssignment assignment = slotTable.lookup(slot)
            .orElseThrow(() -> new RouterException(
                RouterErrorCode.SLOT_NOT_ASSIGNED,
                "Slot " + slot + " not assigned for key=" + key
            ));

        MasterNode primaryNode = nodeRegistry.findNode(assignment.primaryNodeId()).orElse(null);
        if (primaryNode != null && primaryNode.isUp()) {
            return primaryNode;
        }

        return tryReplicaFallback(assignment, key, slot);
    }

    private MasterNode tryReplicaFallback(SlotAssignment assignment, String key, int slot) {
        for (String replicaId : assignment.replicaNodeIds()) {
            MasterNode replica = nodeRegistry.findNode(replicaId).orElse(null);
            if (replica != null && replica.isUp()) {
                fallbackCounter.increment();
                log.info("Falling back to replica={} for key={} slot={}", replicaId, key, slot);
                return replica;
            }
        }

        throw new RouterException(
            RouterErrorCode.NO_AVAILABLE_NODE,
            "No available node for slot=" + slot + " key=" + key
        );
    }
}
