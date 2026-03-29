package com.example.cache.balancer.slot;

import com.example.cache.balancer.error.RouterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotTableTest {

    private SlotTable slotTable;

    @BeforeEach
    void setUp() {
        slotTable = new SlotTable();
    }

    @Test
    void assign_and_lookup_returnsAssignment() {
        slotTable.assign(5, "node-1", List.of("node-2"));

        Optional<SlotAssignment> result = slotTable.lookup(5);

        assertThat(result).isPresent();
        assertThat(result.get().primaryNodeId()).isEqualTo("node-1");
        assertThat(result.get().replicaNodeIds()).containsExactly("node-2");
    }

    @Test
    void lookup_unassignedSlot_returnsEmpty() {
        assertThat(slotTable.lookup(99)).isEmpty();
    }

    @Test
    void requirePrimary_assignedSlot_returnsNodeId() {
        slotTable.assign(10, "node-A", List.of());

        assertThat(slotTable.requirePrimary(10)).isEqualTo("node-A");
    }

    @Test
    void requirePrimary_unassignedSlot_throwsRouterException() {
        assertThatThrownBy(() -> slotTable.requirePrimary(42))
            .isInstanceOf(RouterException.class)
            .hasMessageContaining("slot=42");
    }

    @Test
    void assignBulk_replacesExistingAssignments() {
        slotTable.assign(0, "old-node", List.of());

        List<SlotAssignment> bulk = List.of(
            new SlotAssignment(0, "new-node-1"),
            new SlotAssignment(1, "new-node-2")
        );
        slotTable.assignBulk(bulk);

        assertThat(slotTable.requirePrimary(0)).isEqualTo("new-node-1");
        assertThat(slotTable.requirePrimary(1)).isEqualTo("new-node-2");
    }

    @Test
    void removeNode_removesAllSlotsForThatNode() {
        slotTable.assign(0, "node-1", List.of());
        slotTable.assign(1, "node-1", List.of());
        slotTable.assign(2, "node-2", List.of());

        slotTable.removeNode("node-1");

        assertThat(slotTable.lookup(0)).isEmpty();
        assertThat(slotTable.lookup(1)).isEmpty();
        assertThat(slotTable.lookup(2)).isPresent();
    }

    @Test
    void slotsForNode_returnsOnlyMatchingSlots() {
        slotTable.assign(0, "node-A", List.of());
        slotTable.assign(1, "node-B", List.of());
        slotTable.assign(2, "node-A", List.of());

        List<Integer> slotsA = slotTable.slotsForNode("node-A");
        List<Integer> slotsB = slotTable.slotsForNode("node-B");

        assertThat(slotsA).containsExactlyInAnyOrder(0, 2);
        assertThat(slotsB).containsExactly(1);
    }

    @Test
    void clear_removesAllAssignments() {
        slotTable.assign(0, "node-1", List.of());
        slotTable.assign(1, "node-2", List.of());

        slotTable.clear();

        assertThat(slotTable.assignedSlotCount()).isZero();
        assertThat(slotTable.allAssignments()).isEmpty();
    }

    @Test
    void assignedSlotCount_reflectsCurrentState() {
        assertThat(slotTable.assignedSlotCount()).isZero();

        slotTable.assign(0, "node-1", List.of());
        assertThat(slotTable.assignedSlotCount()).isEqualTo(1);

        slotTable.assign(1, "node-2", List.of());
        assertThat(slotTable.assignedSlotCount()).isEqualTo(2);
    }

    @Test
    void allAssignments_returnsDefensiveCopy() {
        slotTable.assign(0, "node-1", List.of());
        List<SlotAssignment> snapshot = slotTable.allAssignments();

        slotTable.assign(1, "node-2", List.of());

        // Snapshot should not reflect later mutations
        assertThat(snapshot).hasSize(1);
    }
}
