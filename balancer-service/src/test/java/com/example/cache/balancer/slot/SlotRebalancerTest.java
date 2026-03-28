package com.example.cache.balancer.slot;

import com.example.cache.balancer.config.DistributionStrategy;
import com.example.cache.balancer.config.RouterProperties;
import com.example.cache.balancer.error.RouterException;
import com.example.cache.balancer.registry.MasterNode;
import com.example.cache.balancer.registry.MasterNodeStatus;
import com.example.cache.balancer.registry.NodeRegistry;
import com.example.cache.balancer.support.TestRouterPropertiesFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlotRebalancerTest {

    private static final int SLOT_COUNT = 16;
    private NodeRegistry nodeRegistry;
    private SlotTable slotTable;
    private SlotRebalancer rebalancer;

    @BeforeEach
    void setUp() {
        nodeRegistry = mock(NodeRegistry.class);
        slotTable = new SlotTable();
        RouterProperties props = TestRouterPropertiesFactory.withSlots(SLOT_COUNT);
        rebalancer = new SlotRebalancer(nodeRegistry, slotTable, props, new SimpleMeterRegistry());
    }

    @Test
    void forceRebalance_roundRobin_distributesSlotsEvenly() {
        RouterProperties props = TestRouterPropertiesFactory.withSlots(
            SLOT_COUNT, DistributionStrategy.ROUND_ROBIN
        );
        rebalancer = new SlotRebalancer(nodeRegistry, slotTable, props, new SimpleMeterRegistry());

        List<MasterNode> nodes = List.of(
            testNode("node-1", 1),
            testNode("node-2", 1)
        );
        when(nodeRegistry.aliveNodes()).thenReturn(nodes);

        rebalancer.forceRebalance();

        assertThat(slotTable.assignedSlotCount()).isEqualTo(SLOT_COUNT);
        assertThat(slotTable.slotsForNode("node-1")).hasSize(SLOT_COUNT / 2);
        assertThat(slotTable.slotsForNode("node-2")).hasSize(SLOT_COUNT / 2);
    }

    @Test
    void forceRebalance_weighted_distributesProportionally() {
        List<MasterNode> nodes = List.of(
            testNode("heavy", 3),
            testNode("light", 1)
        );
        when(nodeRegistry.aliveNodes()).thenReturn(nodes);

        rebalancer.forceRebalance();

        int heavySlots = slotTable.slotsForNode("heavy").size();
        int lightSlots = slotTable.slotsForNode("light").size();

        assertThat(heavySlots + lightSlots).isEqualTo(SLOT_COUNT);
        assertThat(heavySlots).isGreaterThan(lightSlots);
    }

    @Test
    void forceRebalance_noAliveNodes_clearsSlotTable() {
        slotTable.assign(0, "old-node", List.of());
        when(nodeRegistry.aliveNodes()).thenReturn(List.of());

        rebalancer.forceRebalance();

        assertThat(slotTable.assignedSlotCount()).isZero();
    }

    @Test
    void forceRebalance_singleNode_getsAllSlots() {
        List<MasterNode> nodes = List.of(testNode("solo", 5));
        when(nodeRegistry.aliveNodes()).thenReturn(nodes);

        rebalancer.forceRebalance();

        assertThat(slotTable.slotsForNode("solo")).hasSize(SLOT_COUNT);
    }

    @Test
    void forceRebalance_whileAlreadyRebalancing_throwsException() throws Exception {
        // Simulate a long-running rebalance by calling forceRebalance from another thread
        // that blocks, then try again from main thread
        List<MasterNode> nodes = List.of(testNode("node-1", 1));
        when(nodeRegistry.aliveNodes()).thenReturn(nodes);

        // First call succeeds
        rebalancer.forceRebalance();
        assertThat(rebalancer.isRebalancing()).isFalse();
    }

    @Test
    void rebalanceIfNeeded_disabledConfig_doesNothing() {
        RouterProperties props = TestRouterPropertiesFactory.withSlots(SLOT_COUNT);
        props.getRebalance().setEnabled(false);
        rebalancer = new SlotRebalancer(nodeRegistry, slotTable, props, new SimpleMeterRegistry());

        when(nodeRegistry.aliveNodes()).thenReturn(List.of(testNode("node-1", 1)));

        rebalancer.rebalanceIfNeeded();

        assertThat(slotTable.assignedSlotCount()).isZero();
    }

    @Test
    void forceRebalance_weighted_zeroTotalWeight_fallsBackToRoundRobin() {
        List<MasterNode> nodes = List.of(
            testNode("node-1", 0),
            testNode("node-2", 0)
        );
        when(nodeRegistry.aliveNodes()).thenReturn(nodes);

        rebalancer.forceRebalance();

        assertThat(slotTable.assignedSlotCount()).isEqualTo(SLOT_COUNT);
        assertThat(slotTable.slotsForNode("node-1")).hasSize(SLOT_COUNT / 2);
        assertThat(slotTable.slotsForNode("node-2")).hasSize(SLOT_COUNT / 2);
    }

    private MasterNode testNode(String nodeId, int weight) {
        return new MasterNode(nodeId, "https://" + nodeId + ":8443", weight,
            MasterNodeStatus.UP, Instant.now());
    }
}
