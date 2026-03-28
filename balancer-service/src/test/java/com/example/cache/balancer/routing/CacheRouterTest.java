package com.example.cache.balancer.routing;

import com.example.cache.balancer.client.MasterHttpClient;
import com.example.cache.balancer.client.MasterResponse;
import com.example.cache.balancer.error.RouterException;
import com.example.cache.balancer.registry.MasterNode;
import com.example.cache.balancer.registry.MasterNodeStatus;
import com.example.cache.balancer.registry.NodeRegistry;
import com.example.cache.balancer.slot.SlotAssignment;
import com.example.cache.balancer.slot.SlotCalculator;
import com.example.cache.balancer.slot.SlotTable;
import com.example.cache.balancer.support.TestRouterPropertiesFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheRouterTest {

    private SlotCalculator slotCalculator;
    private SlotTable slotTable;
    private NodeRegistry nodeRegistry;
    private MasterHttpClient masterHttpClient;
    private CacheRouter cacheRouter;

    @BeforeEach
    void setUp() {
        slotCalculator = new SlotCalculator(TestRouterPropertiesFactory.withSlots(64));
        slotTable = new SlotTable();
        nodeRegistry = mock(NodeRegistry.class);
        masterHttpClient = mock(MasterHttpClient.class);
        cacheRouter = new CacheRouter(
            slotCalculator, slotTable, nodeRegistry, masterHttpClient, new SimpleMeterRegistry()
        );
    }

    @Test
    void routeGet_delegatesToPrimaryNode() {
        String key = "test-key";
        int slot = slotCalculator.slotFor(key);
        MasterNode node = aliveNode("node-1");

        slotTable.assign(slot, "node-1", List.of());
        when(nodeRegistry.findNode("node-1")).thenReturn(Optional.of(node));
        when(masterHttpClient.get(node, key))
            .thenReturn(new MasterResponse(HttpStatus.OK, "{\"value\":\"data\"}"));

        MasterResponse response = cacheRouter.routeGet(key);

        assertThat(response.isSuccess()).isTrue();
        verify(masterHttpClient).get(node, key);
    }

    @Test
    void routePut_delegatesToPrimaryNode() {
        String key = "put-key";
        int slot = slotCalculator.slotFor(key);
        MasterNode node = aliveNode("node-1");

        slotTable.assign(slot, "node-1", List.of());
        when(nodeRegistry.findNode("node-1")).thenReturn(Optional.of(node));
        when(masterHttpClient.put(eq(node), eq(key), any()))
            .thenReturn(new MasterResponse(HttpStatus.ACCEPTED, ""));

        MasterResponse response = cacheRouter.routePut(key, "{\"payload\":\"val\"}");

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void routeDelete_delegatesToPrimaryNode() {
        String key = "del-key";
        int slot = slotCalculator.slotFor(key);
        MasterNode node = aliveNode("node-1");

        slotTable.assign(slot, "node-1", List.of());
        when(nodeRegistry.findNode("node-1")).thenReturn(Optional.of(node));
        when(masterHttpClient.delete(node, key))
            .thenReturn(new MasterResponse(HttpStatus.NO_CONTENT, ""));

        MasterResponse response = cacheRouter.routeDelete(key);

        assertThat(response.status().value()).isEqualTo(204);
    }

    @Test
    void routeGet_slotNotAssigned_throwsException() {
        assertThatThrownBy(() -> cacheRouter.routeGet("unassigned-key"))
            .isInstanceOf(RouterException.class)
            .hasMessageContaining("not assigned");
    }

    @Test
    void routeGet_primaryDown_fallsBackToReplica() {
        String key = "failover-key";
        int slot = slotCalculator.slotFor(key);
        MasterNode downNode = downNode("primary");
        MasterNode replicaNode = aliveNode("replica");

        slotTable.assign(slot, "primary", List.of("replica"));
        when(nodeRegistry.findNode("primary")).thenReturn(Optional.of(downNode));
        when(nodeRegistry.findNode("replica")).thenReturn(Optional.of(replicaNode));
        when(masterHttpClient.get(replicaNode, key))
            .thenReturn(new MasterResponse(HttpStatus.OK, "replica-data"));

        MasterResponse response = cacheRouter.routeGet(key);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.body()).isEqualTo("replica-data");
    }

    @Test
    void routeGet_primaryAndReplicasDown_throwsException() {
        String key = "all-down-key";
        int slot = slotCalculator.slotFor(key);

        slotTable.assign(slot, "primary", List.of("replica"));
        when(nodeRegistry.findNode("primary")).thenReturn(Optional.of(downNode("primary")));
        when(nodeRegistry.findNode("replica")).thenReturn(Optional.of(downNode("replica")));

        assertThatThrownBy(() -> cacheRouter.routeGet(key))
            .isInstanceOf(RouterException.class)
            .hasMessageContaining("No available node");
    }

    @Test
    void routeGet_primaryNotFound_fallsBackToReplica() {
        String key = "primary-gone-key";
        int slot = slotCalculator.slotFor(key);
        MasterNode replicaNode = aliveNode("replica");

        slotTable.assign(slot, "primary", List.of("replica"));
        when(nodeRegistry.findNode("primary")).thenReturn(Optional.empty());
        when(nodeRegistry.findNode("replica")).thenReturn(Optional.of(replicaNode));
        when(masterHttpClient.get(replicaNode, key))
            .thenReturn(new MasterResponse(HttpStatus.OK, "ok"));

        MasterResponse response = cacheRouter.routeGet(key);

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void resolveRoutingInfo_returnsSlotAndNodeInfo() {
        String key = "info-key";
        int slot = slotCalculator.slotFor(key);
        slotTable.assign(slot, "node-1", List.of("node-2"));

        RoutingInfo info = cacheRouter.resolveRoutingInfo(key);

        assertThat(info.key()).isEqualTo(key);
        assertThat(info.slot()).isEqualTo(slot);
        assertThat(info.primaryNodeId()).isEqualTo("node-1");
        assertThat(info.replicaNodeIds()).containsExactly("node-2");
    }

    @Test
    void resolveRoutingInfo_unassignedSlot_returnsNullNodeId() {
        RoutingInfo info = cacheRouter.resolveRoutingInfo("no-slot-key");

        assertThat(info.primaryNodeId()).isNull();
        assertThat(info.replicaNodeIds()).isEmpty();
    }

    private MasterNode aliveNode(String nodeId) {
        return new MasterNode(nodeId, "https://" + nodeId + ":8443", 1,
            MasterNodeStatus.UP, Instant.now());
    }

    private MasterNode downNode(String nodeId) {
        return new MasterNode(nodeId, "https://" + nodeId + ":8443", 1,
            MasterNodeStatus.DOWN, Instant.now());
    }
}
