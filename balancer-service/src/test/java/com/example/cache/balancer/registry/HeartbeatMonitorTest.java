package com.example.cache.balancer.registry;

import com.example.cache.balancer.config.HeartbeatProperties;
import com.example.cache.balancer.slot.SlotRebalancer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class HeartbeatMonitorTest {

    private NodeRegistry nodeRegistry;
    private SlotRebalancer slotRebalancer;
    private HeartbeatMonitor heartbeatMonitor;

    @BeforeEach
    void setUp() {
        nodeRegistry = new NodeRegistry(new SimpleMeterRegistry());
        slotRebalancer = mock(SlotRebalancer.class);

        HeartbeatProperties props = new HeartbeatProperties();
        props.setTimeoutMillis(100);

        heartbeatMonitor = new HeartbeatMonitor(nodeRegistry, slotRebalancer, props);
    }

    @Test
    void checkHeartbeats_timedOutNode_marksDown() throws InterruptedException {
        nodeRegistry.register("stale-node", "https://stale:8443", 1);

        Thread.sleep(150);
        heartbeatMonitor.checkHeartbeats();

        MasterNode node = nodeRegistry.requireNode("stale-node");
        assertThat(node.status()).isEqualTo(MasterNodeStatus.DOWN);
    }

    @Test
    void checkHeartbeats_timedOutNode_triggersRebalance() throws InterruptedException {
        nodeRegistry.register("stale-node", "https://stale:8443", 1);

        Thread.sleep(150);
        heartbeatMonitor.checkHeartbeats();

        verify(slotRebalancer).rebalanceIfNeeded();
    }

    @Test
    void checkHeartbeats_freshNode_staysUp() {
        nodeRegistry.register("fresh-node", "https://fresh:8443", 1);

        heartbeatMonitor.checkHeartbeats();

        MasterNode node = nodeRegistry.requireNode("fresh-node");
        assertThat(node.status()).isEqualTo(MasterNodeStatus.UP);
    }

    @Test
    void checkHeartbeats_allFresh_noRebalance() {
        nodeRegistry.register("fresh-node", "https://fresh:8443", 1);

        heartbeatMonitor.checkHeartbeats();

        verify(slotRebalancer, never()).rebalanceIfNeeded();
    }

    @Test
    void checkHeartbeats_alreadyDownNode_isSkipped() throws InterruptedException {
        nodeRegistry.register("down-node", "https://down:8443", 1);
        nodeRegistry.markDown("down-node");

        Thread.sleep(150);
        heartbeatMonitor.checkHeartbeats();

        // Already down, should not trigger rebalance
        verify(slotRebalancer, never()).rebalanceIfNeeded();
    }
}
