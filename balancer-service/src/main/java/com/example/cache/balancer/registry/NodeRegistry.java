package com.example.cache.balancer.registry;

import com.example.cache.balancer.error.RouterErrorCode;
import com.example.cache.balancer.error.RouterException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistry.class);

    private final Map<String, MasterNode> nodes = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public NodeRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gaugeMapSize("router.nodes.total", List.of(), nodes);
    }

    public MasterNode register(String nodeId, String baseUrl, int weight) {
        Instant now = Instant.now();
        MasterNode node = nodes.compute(nodeId, (id, existing) ->
            createOrUpdate(id, baseUrl, weight, existing, now)
        );
        log.info("Registered node={} baseUrl={} weight={}", nodeId, baseUrl, weight);
        return node;
    }

    public void heartbeat(String nodeId) {
        nodes.computeIfPresent(nodeId, (id, existing) -> existing.withHeartbeat(Instant.now()));
    }

    public void markDown(String nodeId) {
        nodes.computeIfPresent(nodeId, (id, existing) -> existing.withStatus(MasterNodeStatus.DOWN));
        log.warn("Node marked DOWN nodeId={}", nodeId);
    }

    public void markDraining(String nodeId) {
        nodes.computeIfPresent(nodeId, (id, existing) -> existing.withStatus(MasterNodeStatus.DRAINING));
        log.info("Node marked DRAINING nodeId={}", nodeId);
    }

    public void deregister(String nodeId) {
        MasterNode removed = nodes.remove(nodeId);
        if (removed != null) {
            log.info("Deregistered node={}", nodeId);
        }
    }

    public MasterNode requireNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId))
            .orElseThrow(() -> new RouterException(
                RouterErrorCode.NODE_NOT_FOUND,
                "Node not found: " + nodeId
            ));
    }

    public Optional<MasterNode> findNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    public List<MasterNode> aliveNodes() {
        List<MasterNode> alive = new ArrayList<>();
        nodes.values().forEach(node -> {
            if (node.isUp()) {
                alive.add(node);
            }
        });
        alive.sort(Comparator.comparing(MasterNode::nodeId));
        return alive;
    }

    public List<MasterNode> allNodes() {
        List<MasterNode> all = new ArrayList<>(nodes.values());
        all.sort(Comparator.comparing(MasterNode::nodeId));
        return all;
    }

    public int aliveNodeCount() {
        int count = 0;
        for (MasterNode node : nodes.values()) {
            if (node.isUp()) {
                count++;
            }
        }
        return count;
    }

    private MasterNode createOrUpdate(String nodeId, String baseUrl, int weight,
                                      MasterNode existing, Instant now) {
        if (existing == null) {
            return new MasterNode(nodeId, baseUrl, weight, MasterNodeStatus.UP, now);
        }
        return new MasterNode(nodeId, baseUrl, weight, MasterNodeStatus.UP, now);
    }
}
