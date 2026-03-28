package com.example.cache.balancer.admin;

import com.example.cache.balancer.api.NodeRegistrationRequest;
import com.example.cache.balancer.api.NodeView;
import com.example.cache.balancer.api.SlotTableView;
import com.example.cache.balancer.error.RouterErrorCode;
import com.example.cache.balancer.error.RouterException;
import com.example.cache.balancer.registry.MasterNode;
import com.example.cache.balancer.registry.NodeRegistry;
import com.example.cache.balancer.slot.SlotAssignment;
import com.example.cache.balancer.slot.SlotRebalancer;
import com.example.cache.balancer.slot.SlotTable;
import com.example.cache.balancer.slot.SlotCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final NodeRegistry nodeRegistry;
    private final SlotTable slotTable;
    private final SlotCalculator slotCalculator;
    private final SlotRebalancer slotRebalancer;

    public AdminController(NodeRegistry nodeRegistry,
                           SlotTable slotTable,
                           SlotCalculator slotCalculator,
                           SlotRebalancer slotRebalancer) {
        this.nodeRegistry = nodeRegistry;
        this.slotTable = slotTable;
        this.slotCalculator = slotCalculator;
        this.slotRebalancer = slotRebalancer;
    }

    @PostMapping("/nodes")
    public ResponseEntity<NodeView> registerNode(@RequestBody NodeRegistrationRequest request) {
        validateRegistration(request);
        MasterNode node = nodeRegistry.register(request.nodeId(), request.baseUrl(), request.weight());
        slotRebalancer.rebalanceIfNeeded();
        return ResponseEntity.status(HttpStatus.CREATED).body(toNodeView(node));
    }

    @PostMapping("/nodes/{nodeId}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable String nodeId) {
        nodeRegistry.heartbeat(nodeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/nodes/{nodeId}/drain")
    public ResponseEntity<Void> drainNode(@PathVariable String nodeId) {
        nodeRegistry.requireNode(nodeId);
        nodeRegistry.markDraining(nodeId);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> deregisterNode(@PathVariable String nodeId) {
        nodeRegistry.deregister(nodeId);
        slotTable.removeNode(nodeId);
        slotRebalancer.rebalanceIfNeeded();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/nodes")
    public ResponseEntity<List<NodeView>> listNodes() {
        List<NodeView> views = nodeRegistry.allNodes().stream()
            .map(this::toNodeView)
            .toList();
        return ResponseEntity.ok(views);
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<NodeView> getNode(@PathVariable String nodeId) {
        MasterNode node = nodeRegistry.requireNode(nodeId);
        return ResponseEntity.ok(toNodeView(node));
    }

    @PostMapping("/rebalance")
    public ResponseEntity<SlotTableView> rebalance() {
        slotRebalancer.forceRebalance();
        return ResponseEntity.ok(buildSlotTableView());
    }

    @GetMapping("/slots")
    public ResponseEntity<SlotTableView> getSlotTable() {
        return ResponseEntity.ok(buildSlotTableView());
    }

    private void validateRegistration(NodeRegistrationRequest request) {
        if (request.nodeId() == null || request.nodeId().isBlank()) {
            throw new RouterException(RouterErrorCode.VALIDATION_ERROR, "nodeId must not be blank");
        }
        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            throw new RouterException(RouterErrorCode.VALIDATION_ERROR, "baseUrl must not be blank");
        }
        if (request.weight() <= 0) {
            throw new RouterException(RouterErrorCode.VALIDATION_ERROR, "weight must be positive");
        }
    }

    private NodeView toNodeView(MasterNode node) {
        int assignedSlots = slotTable.slotsForNode(node.nodeId()).size();
        return new NodeView(
            node.nodeId(),
            node.baseUrl(),
            node.weight(),
            node.status(),
            node.lastHeartbeat(),
            assignedSlots
        );
    }

    private SlotTableView buildSlotTableView() {
        List<SlotAssignment> assignments = slotTable.allAssignments();
        Map<String, Integer> slotsPerNode = new HashMap<>();
        for (SlotAssignment assignment : assignments) {
            slotsPerNode.merge(assignment.primaryNodeId(), 1, Integer::sum);
        }
        return new SlotTableView(
            slotCalculator.getSlotCount(),
            slotTable.assignedSlotCount(),
            slotsPerNode,
            assignments
        );
    }
}
