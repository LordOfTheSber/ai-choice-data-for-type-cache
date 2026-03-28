package com.example.cache.balancer.admin;

import com.example.cache.balancer.api.NodeRegistrationRequest;
import com.example.cache.balancer.registry.MasterNodeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerNode_returnsCreatedWithNodeView() throws Exception {
        NodeRegistrationRequest request = new NodeRegistrationRequest(
            "integration-node-1", "https://node1:8443", 5
        );

        mockMvc.perform(post("/admin/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nodeId", is("integration-node-1")))
            .andExpect(jsonPath("$.weight", is(5)))
            .andExpect(jsonPath("$.status", is(MasterNodeStatus.UP.name())));
    }

    @Test
    void registerNode_invalidRequest_returnsBadRequest() throws Exception {
        NodeRegistrationRequest request = new NodeRegistrationRequest("", "https://node:8443", 1);

        mockMvc.perform(post("/admin/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }

    @Test
    void registerNode_zeroWeight_returnsBadRequest() throws Exception {
        NodeRegistrationRequest request = new NodeRegistrationRequest("node-bad", "https://node:8443", 0);

        mockMvc.perform(post("/admin/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void heartbeat_registeredNode_returnsOk() throws Exception {
        registerTestNode("hb-node");

        mockMvc.perform(post("/admin/nodes/hb-node/heartbeat"))
            .andExpect(status().isOk());
    }

    @Test
    void drainNode_registeredNode_returnsAccepted() throws Exception {
        registerTestNode("drain-node");

        mockMvc.perform(post("/admin/nodes/drain-node/drain"))
            .andExpect(status().isAccepted());
    }

    @Test
    void drainNode_unknownNode_returnsNotFound() throws Exception {
        mockMvc.perform(post("/admin/nodes/unknown-node/drain"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deregisterNode_returnsNoContent() throws Exception {
        registerTestNode("del-node");

        mockMvc.perform(delete("/admin/nodes/del-node"))
            .andExpect(status().isNoContent());
    }

    @Test
    void getNode_registeredNode_returnsOk() throws Exception {
        registerTestNode("get-node");

        mockMvc.perform(get("/admin/nodes/get-node"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId", is("get-node")));
    }

    @Test
    void getNode_unknownNode_returnsNotFound() throws Exception {
        mockMvc.perform(get("/admin/nodes/nonexistent"))
            .andExpect(status().isNotFound());
    }

    @Test
    void listNodes_returnsAllRegisteredNodes() throws Exception {
        registerTestNode("list-node-a");
        registerTestNode("list-node-b");

        mockMvc.perform(get("/admin/nodes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void rebalance_returnsSlotTable() throws Exception {
        registerTestNode("rebalance-node");

        mockMvc.perform(post("/admin/rebalance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSlots", is(64)))
            .andExpect(jsonPath("$.assignedSlots").isNumber());
    }

    @Test
    void getSlotTable_returnsCurrentState() throws Exception {
        mockMvc.perform(get("/admin/slots"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSlots", is(64)));
    }

    private void registerTestNode(String nodeId) throws Exception {
        NodeRegistrationRequest request = new NodeRegistrationRequest(
            nodeId, "https://" + nodeId + ":8443", 3
        );
        mockMvc.perform(post("/admin/nodes")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }
}
