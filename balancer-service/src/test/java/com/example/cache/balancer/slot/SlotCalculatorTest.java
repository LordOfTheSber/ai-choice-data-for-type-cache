package com.example.cache.balancer.slot;

import com.example.cache.balancer.support.TestRouterPropertiesFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorTest {

    private static final int SLOT_COUNT = 64;
    private SlotCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SlotCalculator(TestRouterPropertiesFactory.withSlots(SLOT_COUNT));
    }

    @Test
    void slotFor_returnsSameSlotForSameKey() {
        String key = "user:12345";
        int slot1 = calculator.slotFor(key);
        int slot2 = calculator.slotFor(key);

        assertThat(slot1).isEqualTo(slot2);
    }

    @Test
    void slotFor_returnsValueWithinRange() {
        for (int idx = 0; idx < 1000; idx++) {
            int slot = calculator.slotFor("key-" + idx);
            assertThat(slot).isBetween(0, SLOT_COUNT - 1);
        }
    }

    @Test
    void slotFor_distributesKeysReasonablyAcrossSlots() {
        Map<Integer, Integer> distribution = new HashMap<>();
        int keyCount = 10_000;

        for (int idx = 0; idx < keyCount; idx++) {
            int slot = calculator.slotFor("distributed-key-" + idx);
            distribution.merge(slot, 1, Integer::sum);
        }

        // Every slot should get at least some keys with 10k keys across 64 slots
        assertThat(distribution.size()).isEqualTo(SLOT_COUNT);

        int expectedPerSlot = keyCount / SLOT_COUNT;
        int tolerance = expectedPerSlot; // 100% tolerance — just verify no slot is starved
        for (int count : distribution.values()) {
            assertThat(count).isGreaterThan(0);
            assertThat(count).isLessThan(expectedPerSlot + tolerance);
        }
    }

    @Test
    void slotFor_differentKeysDifferentSlots() {
        int slotA = calculator.slotFor("keyA");
        int slotB = calculator.slotFor("completely-different-key-B");

        // Not guaranteed for all pairs, but these specific keys should differ
        // This test validates the hash function produces varied output
        assertThat(slotA).isNotEqualTo(slotB);
    }

    @Test
    void slotFor_emptyKeyProducesValidSlot() {
        int slot = calculator.slotFor("");
        assertThat(slot).isBetween(0, SLOT_COUNT - 1);
    }

    @Test
    void getSlotCount_returnsConfiguredValue() {
        assertThat(calculator.getSlotCount()).isEqualTo(SLOT_COUNT);
    }
}
