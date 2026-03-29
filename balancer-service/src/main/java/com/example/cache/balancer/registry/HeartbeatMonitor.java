package com.example.cache.balancer.registry;

import com.example.cache.balancer.config.HeartbeatProperties;
import com.example.cache.balancer.slot.SlotRebalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Periodically checks master node heartbeats and marks timed-out nodes as DOWN.
 * Triggers slot rebalancing when nodes go offline.
 */
@Component
public class HeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final NodeRegistry nodeRegistry;
    private final SlotRebalancer slotRebalancer;
    private final long timeoutMillis;

    public HeartbeatMonitor(NodeRegistry nodeRegistry,
                            SlotRebalancer slotRebalancer,
                            HeartbeatProperties heartbeatProperties) {
        this.nodeRegistry = nodeRegistry;
        this.slotRebalancer = slotRebalancer;
        this.timeoutMillis = heartbeatProperties.getTimeoutMillis();
    }

    @Scheduled(fixedDelayString = "${heartbeat.check-interval-millis:2000}")
    public void checkHeartbeats() {
        Instant now = Instant.now();
        List<MasterNode> allNodes = nodeRegistry.allNodes();
        boolean anyMarkedDown = false;

        for (MasterNode node : allNodes) {
            if (isTimedOut(node, now)) {
                nodeRegistry.markDown(node.nodeId());
                anyMarkedDown = true;
            }
        }

        if (anyMarkedDown) {
            log.info("Detected timed-out nodes, triggering rebalance check");
            slotRebalancer.rebalanceIfNeeded();
        }
    }

    private boolean isTimedOut(MasterNode node, Instant now) {
        if (!node.isUp()) {
            return false;
        }
        long idleMillis = now.toEpochMilli() - node.lastHeartbeat().toEpochMilli();
        return idleMillis > timeoutMillis;
    }
}
