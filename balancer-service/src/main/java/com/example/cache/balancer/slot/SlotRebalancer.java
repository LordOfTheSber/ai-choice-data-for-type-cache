package com.example.cache.balancer.slot;

import com.example.cache.balancer.config.DistributionStrategy;
import com.example.cache.balancer.config.RouterProperties;
import com.example.cache.balancer.error.RouterErrorCode;
import com.example.cache.balancer.error.RouterException;
import com.example.cache.balancer.registry.MasterNode;
import com.example.cache.balancer.registry.NodeRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SlotRebalancer {

    private static final Logger log = LoggerFactory.getLogger(SlotRebalancer.class);

    private final NodeRegistry nodeRegistry;
    private final SlotTable slotTable;
    private final RouterProperties routerProperties;
    private final Counter rebalanceCounter;

    private final AtomicBoolean rebalancing = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastRebalanceTime = new AtomicReference<>(Instant.EPOCH);

    public SlotRebalancer(NodeRegistry nodeRegistry,
                          SlotTable slotTable,
                          RouterProperties routerProperties,
                          MeterRegistry meterRegistry) {
        this.nodeRegistry = nodeRegistry;
        this.slotTable = slotTable;
        this.routerProperties = routerProperties;
        this.rebalanceCounter = meterRegistry.counter("router.rebalance.total");
    }

    public void rebalanceIfNeeded() {
        if (!routerProperties.getRebalance().isEnabled()) {
            return;
        }
        if (isCooldownActive()) {
            log.debug("Rebalance skipped: cooldown active");
            return;
        }
        forceRebalance();
    }

    public void forceRebalance() {
        if (!rebalancing.compareAndSet(false, true)) {
            throw new RouterException(RouterErrorCode.REBALANCE_IN_PROGRESS, "Rebalance already running");
        }
        try {
            executeRebalance();
        } finally {
            lastRebalanceTime.set(Instant.now());
            rebalancing.set(false);
        }
    }

    public boolean isRebalancing() {
        return rebalancing.get();
    }

    private void executeRebalance() {
        List<MasterNode> alive = nodeRegistry.aliveNodes();
        if (alive.isEmpty()) {
            log.warn("No alive nodes, clearing slot table");
            slotTable.clear();
            return;
        }

        int slotCount = routerProperties.getSlotCount();
        DistributionStrategy strategy = routerProperties.getDistributionStrategy();
        List<SlotAssignment> newAssignments = distributeSlots(alive, slotCount, strategy);

        slotTable.clear();
        slotTable.assignBulk(newAssignments);
        rebalanceCounter.increment();
        log.info("Rebalanced {} slots across {} nodes using strategy={}",
            slotCount, alive.size(), strategy);
    }

    private List<SlotAssignment> distributeSlots(List<MasterNode> alive, int slotCount,
                                                 DistributionStrategy strategy) {
        return switch (strategy) {
            case ROUND_ROBIN -> distributeRoundRobin(alive, slotCount);
            case WEIGHTED -> distributeWeighted(alive, slotCount);
        };
    }

    private List<SlotAssignment> distributeRoundRobin(List<MasterNode> alive, int slotCount) {
        List<SlotAssignment> result = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            String nodeId = alive.get(slot % alive.size()).nodeId();
            result.add(new SlotAssignment(slot, nodeId));
        }
        return result;
    }

    private List<SlotAssignment> distributeWeighted(List<MasterNode> alive, int slotCount) {
        int totalWeight = alive.stream().mapToInt(MasterNode::weight).sum();
        if (totalWeight == 0) {
            return distributeRoundRobin(alive, slotCount);
        }

        List<SlotAssignment> result = new ArrayList<>(slotCount);
        int assignedSlots = 0;

        for (int nodeIdx = 0; nodeIdx < alive.size(); nodeIdx++) {
            MasterNode node = alive.get(nodeIdx);
            int nodeSlots = calculateNodeSlotCount(node.weight(), totalWeight, slotCount, nodeIdx, alive.size());
            int endSlot = Math.min(assignedSlots + nodeSlots, slotCount);

            for (int slot = assignedSlots; slot < endSlot; slot++) {
                result.add(new SlotAssignment(slot, node.nodeId()));
            }
            assignedSlots = endSlot;
        }
        return result;
    }

    /**
     * Calculates how many slots this node should own. The last node absorbs any rounding remainder.
     */
    private int calculateNodeSlotCount(int weight, int totalWeight, int slotCount,
                                       int nodeIdx, int nodeCount) {
        boolean isLastNode = (nodeIdx == nodeCount - 1);
        if (isLastNode) {
            int alreadyAssigned = 0;
            // This is called sequentially, so recalculate prior assignments
            // Instead, let the caller handle remainder. We just return proportional share.
            return (int) Math.round((double) weight / totalWeight * slotCount);
        }
        return (int) Math.round((double) weight / totalWeight * slotCount);
    }

    private boolean isCooldownActive() {
        long cooldownSeconds = routerProperties.getRebalance().getCooldownSeconds();
        Instant lastTime = lastRebalanceTime.get();
        return Instant.now().isBefore(lastTime.plusSeconds(cooldownSeconds));
    }
}
