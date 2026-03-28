package com.example.cache.balancer.registry;

import com.example.cache.balancer.error.RouterException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeRegistryTest {

    private NodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry(new SimpleMeterRegistry());
    }

    @Test
    void register_createsNewNode() {
        MasterNode node = registry.register("node-1", "https://node-1:8443", 5);

        assertThat(node.nodeId()).isEqualTo("node-1");
        assertThat(node.baseUrl()).isEqualTo("https://node-1:8443");
        assertThat(node.weight()).isEqualTo(5);
        assertThat(node.status()).isEqualTo(MasterNodeStatus.UP);
    }

    @Test
    void register_existingNode_updatesWeightAndStatus() {
        registry.register("node-1", "https://node-1:8443", 3);
        registry.markDown("node-1");

        MasterNode updated = registry.register("node-1", "https://node-1:8443", 7);

        assertThat(updated.weight()).isEqualTo(7);
        assertThat(updated.status()).isEqualTo(MasterNodeStatus.UP);
    }

    @Test
    void heartbeat_updatesLastHeartbeatTimestamp() throws InterruptedException {
        registry.register("node-1", "https://node-1:8443", 1);
        MasterNode before = registry.requireNode("node-1");

        Thread.sleep(10);
        registry.heartbeat("node-1");
        MasterNode after = registry.requireNode("node-1");

        assertThat(after.lastHeartbeat()).isAfter(before.lastHeartbeat());
    }

    @Test
    void heartbeat_unknownNode_doesNothing() {
        // Should not throw
        registry.heartbeat("nonexistent");
    }

    @Test
    void markDown_changesStatus() {
        registry.register("node-1", "https://node-1:8443", 1);

        registry.markDown("node-1");

        assertThat(registry.requireNode("node-1").status()).isEqualTo(MasterNodeStatus.DOWN);
    }

    @Test
    void markDraining_changesStatus() {
        registry.register("node-1", "https://node-1:8443", 1);

        registry.markDraining("node-1");

        assertThat(registry.requireNode("node-1").status()).isEqualTo(MasterNodeStatus.DRAINING);
    }

    @Test
    void deregister_removesNode() {
        registry.register("node-1", "https://node-1:8443", 1);

        registry.deregister("node-1");

        assertThat(registry.findNode("node-1")).isEmpty();
    }

    @Test
    void deregister_unknownNode_doesNothing() {
        registry.deregister("nonexistent");
        // No exception
    }

    @Test
    void requireNode_missingNode_throwsException() {
        assertThatThrownBy(() -> registry.requireNode("missing"))
            .isInstanceOf(RouterException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void findNode_existingNode_returnsPresent() {
        registry.register("node-1", "https://node-1:8443", 1);

        Optional<MasterNode> found = registry.findNode("node-1");

        assertThat(found).isPresent();
        assertThat(found.get().nodeId()).isEqualTo("node-1");
    }

    @Test
    void aliveNodes_excludesDownAndDrainingNodes() {
        registry.register("up-node", "https://up:8443", 1);
        registry.register("down-node", "https://down:8443", 1);
        registry.register("draining-node", "https://draining:8443", 1);

        registry.markDown("down-node");
        registry.markDraining("draining-node");

        List<MasterNode> alive = registry.aliveNodes();

        assertThat(alive).hasSize(1);
        assertThat(alive.get(0).nodeId()).isEqualTo("up-node");
    }

    @Test
    void allNodes_returnsAllNodesSorted() {
        registry.register("node-b", "https://b:8443", 1);
        registry.register("node-a", "https://a:8443", 1);
        registry.register("node-c", "https://c:8443", 1);

        List<MasterNode> all = registry.allNodes();

        assertThat(all).hasSize(3);
        assertThat(all.get(0).nodeId()).isEqualTo("node-a");
        assertThat(all.get(1).nodeId()).isEqualTo("node-b");
        assertThat(all.get(2).nodeId()).isEqualTo("node-c");
    }

    @Test
    void aliveNodeCount_countsOnlyUpNodes() {
        registry.register("node-1", "https://n1:8443", 1);
        registry.register("node-2", "https://n2:8443", 1);
        registry.markDown("node-2");

        assertThat(registry.aliveNodeCount()).isEqualTo(1);
    }
}
