package com.example.cache.balancer.routing;

import com.example.cache.balancer.registry.MasterNode;
import com.example.cache.balancer.registry.MasterNodeStatus;
import com.example.cache.balancer.registry.NodeRegistry;
import com.example.cache.balancer.slot.SlotCalculator;
import com.example.cache.balancer.slot.SlotRebalancer;
import com.example.cache.balancer.slot.SlotTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CacheRouterControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SlotCalculator slotCalculator;

    @Autowired
    private SlotTable slotTable;

    @Autowired
    private NodeRegistry nodeRegistry;

    @Test
    void getRouting_assignedSlot_returnsRoutingInfo() throws Exception {
        String key = "routing-test-key";
        int slot = slotCalculator.slotFor(key);

        nodeRegistry.register("route-node", "https://route-node:8443", 1);
        slotTable.assign(slot, "route-node", List.of("replica-node"));

        mockMvc.perform(get("/api/cache/{key}/routing", key))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key", is(key)))
            .andExpect(jsonPath("$.slot", is(slot)))
            .andExpect(jsonPath("$.primaryNodeId", is("route-node")))
            .andExpect(jsonPath("$.replicaNodeIds[0]", is("replica-node")));
    }

    @Test
    void getRouting_returnsSlotField() throws Exception {
        mockMvc.perform(get("/api/cache/any-key/routing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key", is("any-key")))
            .andExpect(jsonPath("$.slot").isNumber());
    }

    @Test
    void getCache_slotNotAssigned_returnsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/api/cache/no-slot-key"))
            .andExpect(status().isServiceUnavailable());
    }
}
