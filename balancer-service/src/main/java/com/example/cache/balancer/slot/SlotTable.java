package com.example.cache.balancer.slot;

import com.example.cache.balancer.error.RouterErrorCode;
import com.example.cache.balancer.error.RouterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe mapping of slot numbers to their assigned master nodes (primary + replicas).
 * Uses a read-write lock: reads (hot-path) are concurrent, writes (rebalance) are exclusive.
 */
@Component
public class SlotTable {

    private static final Logger log = LoggerFactory.getLogger(SlotTable.class);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Integer, SlotAssignment> assignments = new HashMap<>();

    public Optional<SlotAssignment> lookup(int slot) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(assignments.get(slot));
        } finally {
            lock.readLock().unlock();
        }
    }

    public String requirePrimary(int slot) {
        return lookup(slot)
            .map(SlotAssignment::primaryNodeId)
            .orElseThrow(() -> new RouterException(
                RouterErrorCode.SLOT_NOT_ASSIGNED,
                "No node assigned for slot=" + slot
            ));
    }

    public void assign(int slot, String primaryNodeId, List<String> replicaNodeIds) {
        lock.writeLock().lock();
        try {
            assignments.put(slot, new SlotAssignment(slot, primaryNodeId, List.copyOf(replicaNodeIds)));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void assignBulk(List<SlotAssignment> bulk) {
        lock.writeLock().lock();
        try {
            for (SlotAssignment entry : bulk) {
                assignments.put(entry.slot(), entry);
            }
            log.info("Bulk assigned {} slots", bulk.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeNode(String nodeId) {
        lock.writeLock().lock();
        try {
            assignments.entrySet().removeIf(entry -> nodeId.equals(entry.getValue().primaryNodeId()));
            log.info("Removed all primary assignments for nodeId={}", nodeId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<SlotAssignment> allAssignments() {
        lock.readLock().lock();
        try {
            return List.copyOf(assignments.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Integer> slotsForNode(String nodeId) {
        lock.readLock().lock();
        try {
            List<Integer> slots = new ArrayList<>();
            for (SlotAssignment assignment : assignments.values()) {
                if (nodeId.equals(assignment.primaryNodeId())) {
                    slots.add(assignment.slot());
                }
            }
            return Collections.unmodifiableList(slots);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int assignedSlotCount() {
        lock.readLock().lock();
        try {
            return assignments.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            assignments.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
