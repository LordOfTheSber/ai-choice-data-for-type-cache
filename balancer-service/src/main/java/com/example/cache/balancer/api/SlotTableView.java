package com.example.cache.balancer.api;

import com.example.cache.balancer.slot.SlotAssignment;

import java.util.List;
import java.util.Map;

public record SlotTableView(
    int totalSlots,
    int assignedSlots,
    Map<String, Integer> slotsPerNode,
    List<SlotAssignment> assignments
) {
}
