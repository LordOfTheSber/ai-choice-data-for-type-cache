package com.example.cache.balancer.slot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SlotTableConcurrencyTest {

    private SlotTable slotTable;

    @BeforeEach
    void setUp() {
        slotTable = new SlotTable();
    }

    @Test
    void concurrentReadsAndWrites_doNotCorruptState() throws Exception {
        int threadCount = 8;
        int operationsPerThread = 5_000;
        int slotCount = 64;

        // Pre-populate some slots
        for (int slot = 0; slot < slotCount; slot++) {
            slotTable.assign(slot, "initial-node", List.of());
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int thread = 0; thread < threadCount; thread++) {
            final int threadId = thread;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    runMixedOperations(threadId, operationsPerThread, slotCount, errors);
                } catch (Exception exception) {
                    errors.incrementAndGet();
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(errors.get()).isZero();
    }

    @Test
    void concurrentBulkAssignAndLookup_remainsConsistent() throws Exception {
        int readerCount = 6;
        int slotCount = 32;
        int iterations = 1_000;

        ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
        CyclicBarrier barrier = new CyclicBarrier(readerCount + 1);
        AtomicInteger errors = new AtomicInteger(0);

        // Writer thread: repeatedly reassigns all slots
        Future<?> writer = executor.submit(() -> {
            try {
                barrier.await();
                for (int iter = 0; iter < iterations; iter++) {
                    List<SlotAssignment> bulk = new ArrayList<>();
                    String nodeId = "node-" + (iter % 3);
                    for (int slot = 0; slot < slotCount; slot++) {
                        bulk.add(new SlotAssignment(slot, nodeId));
                    }
                    slotTable.assignBulk(bulk);
                }
            } catch (Exception exception) {
                errors.incrementAndGet();
            }
        });

        // Reader threads: continuously look up slots
        List<Future<?>> readers = new ArrayList<>();
        for (int reader = 0; reader < readerCount; reader++) {
            readers.add(executor.submit(() -> {
                try {
                    barrier.await();
                    for (int iter = 0; iter < iterations * 10; iter++) {
                        int slot = iter % slotCount;
                        Optional<SlotAssignment> assignment = slotTable.lookup(slot);
                        // Assignment may be absent during clear, but should never be corrupted
                        assignment.ifPresent(slotAssignment -> {
                            if (slotAssignment.primaryNodeId() == null) {
                                errors.incrementAndGet();
                            }
                        });
                    }
                } catch (Exception exception) {
                    errors.incrementAndGet();
                }
            }));
        }

        writer.get(30, TimeUnit.SECONDS);
        for (Future<?> future : readers) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(errors.get()).isZero();
    }

    @Test
    void concurrentRemoveAndLookup_noCrash() throws Exception {
        int slotCount = 16;
        for (int slot = 0; slot < slotCount; slot++) {
            slotTable.assign(slot, "node-to-remove", List.of());
        }

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        // One thread removes, others read
        futures.add(executor.submit(() -> {
            try {
                barrier.await();
                slotTable.removeNode("node-to-remove");
            } catch (Exception exception) {
                errors.incrementAndGet();
            }
        }));

        for (int thread = 1; thread < threadCount; thread++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    for (int iter = 0; iter < 10_000; iter++) {
                        slotTable.lookup(iter % slotCount);
                        slotTable.assignedSlotCount();
                        slotTable.allAssignments();
                    }
                } catch (Exception exception) {
                    errors.incrementAndGet();
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(errors.get()).isZero();
    }

    private void runMixedOperations(int threadId, int operationsPerThread,
                                    int slotCount, AtomicInteger errors) {
        for (int op = 0; op < operationsPerThread; op++) {
            try {
                int slot = op % slotCount;
                if (op % 3 == 0) {
                    slotTable.assign(slot, "node-" + threadId, List.of());
                } else if (op % 3 == 1) {
                    slotTable.lookup(slot);
                } else {
                    slotTable.slotsForNode("node-" + threadId);
                }
            } catch (Exception exception) {
                errors.incrementAndGet();
            }
        }
    }
}
