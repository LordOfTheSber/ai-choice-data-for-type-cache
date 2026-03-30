package com.example.cache.master.cluster;

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
public class MasterNodeRegistryService {
    private static final Logger log = LoggerFactory.getLogger(MasterNodeRegistryService.class);

    private final Map<String, NodeState> nodes;
    private final long heartbeatTimeoutMillis;

    public MasterNodeRegistryService(ClusterProperties clusterProperties) {
        this.nodes = new ConcurrentHashMap<>();
        this.heartbeatTimeoutMillis = clusterProperties.getHeartbeatTimeoutMillis();
    }

    public void register(String nodeId, int weight) {
        nodes.compute(nodeId, (id, existing) -> registerNode(id, existing, weight));
        log.info("Registered node={} weight={}", nodeId, weight);
    }

    public void heartbeat(String nodeId) {
        nodes.computeIfPresent(nodeId, (ignored, node) -> node.withHeartbeat(Instant.now()));
    }

    public void markDown(String nodeId) {
        nodes.computeIfPresent(nodeId, (ignored, node) -> node.withStatus(MasterNodeStatus.DOWN));
        log.warn("Node marked down nodeId={}", nodeId);
    }

    public Optional<String> route(String key) {
        List<NodeState> aliveNodes = aliveNodesSnapshot();
        if (aliveNodes.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.floorMod(key.hashCode(), aliveNodes.size());
        return Optional.of(aliveNodes.get(index).nodeId());
    }

    public List<MasterNodeView> snapshot() {
        List<MasterNodeView> view = new ArrayList<>();
        nodes.values().forEach(node -> view.add(node.toView()));
        view.sort(Comparator.comparing(MasterNodeView::nodeId));
        return view;
    }

    private NodeState registerNode(String nodeId, NodeState existing, int weight) {
        Instant now = Instant.now();
        if (existing == null) {
            return new NodeState(nodeId, weight, now, MasterNodeStatus.UP);
        }
        return existing.withWeight(weight).withStatus(MasterNodeStatus.UP).withHeartbeat(now);
    }

    private List<NodeState> aliveNodesSnapshot() {
        Instant now = Instant.now();
        List<NodeState> aliveNodes = new ArrayList<>();
        nodes.forEach((nodeId, state) -> addIfAlive(aliveNodes, nodeId, state, now));
        aliveNodes.sort(Comparator.comparing(NodeState::nodeId));
        return aliveNodes;
    }

    private void addIfAlive(List<NodeState> aliveNodes, String nodeId, NodeState state, Instant now) {
        if (state.status() != MasterNodeStatus.UP) {
            return;
        }
        long idleMillis = now.toEpochMilli() - state.lastHeartbeat().toEpochMilli();
        if (idleMillis > heartbeatTimeoutMillis) {
            nodes.computeIfPresent(nodeId, (ignored, node) -> node.withStatus(MasterNodeStatus.DOWN));
            return;
        }
        aliveNodes.add(state);
    }

    private record NodeState(
        String nodeId,
        int weight,
        Instant lastHeartbeat,
        MasterNodeStatus status
    ) {
        private NodeState withHeartbeat(Instant value) {
            return new NodeState(nodeId, weight, value, MasterNodeStatus.UP);
        }

        private NodeState withStatus(MasterNodeStatus value) {
            return new NodeState(nodeId, weight, lastHeartbeat, value);
        }

        private NodeState withWeight(int value) {
            return new NodeState(nodeId, value, lastHeartbeat, status);
        }

        private MasterNodeView toView() {
            return new MasterNodeView(nodeId, status, lastHeartbeat, weight);
        }
    }
}
