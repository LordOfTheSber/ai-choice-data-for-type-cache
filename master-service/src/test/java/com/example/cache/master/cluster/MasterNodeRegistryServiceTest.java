package com.example.cache.master.cluster;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MasterNodeRegistryServiceTest {

    @Test
    void routeShouldChooseAliveNode() {
        MasterNodeRegistryService registry = registry(10_000);
        registry.register("node-a", 1);
        registry.register("node-b", 1);

        Optional<String> route = registry.route("book-1");

        assertTrue(route.isPresent());
        assertTrue(route.get().equals("node-a") || route.get().equals("node-b"));
    }

    @Test
    void routeShouldFailoverWhenNodeMarkedDown() {
        MasterNodeRegistryService registry = registry(10_000);
        registry.register("node-a", 1);
        registry.register("node-b", 1);
        registry.markDown("node-a");

        for (int iteration = 0; iteration < 20; iteration++) {
            assertEquals("node-b", registry.route("key-" + iteration).orElseThrow());
        }
    }

    @Test
    void heartbeatTimeoutShouldExcludeNodeFromRouting() throws InterruptedException {
        MasterNodeRegistryService registry = registry(30);
        registry.register("node-a", 1);

        Thread.sleep(60);

        assertTrue(registry.route("book").isEmpty());
        assertEquals(MasterNodeStatus.DOWN, registry.snapshot().get(0).status());
    }

    @Test
    void heartbeatShouldRecoverNodeAfterTimeout() throws InterruptedException {
        MasterNodeRegistryService registry = registry(30);
        registry.register("node-a", 1);
        Thread.sleep(60);
        assertTrue(registry.route("book").isEmpty());

        registry.register("node-a", 1);
        registry.heartbeat("node-a");

        assertEquals("node-a", registry.route("book").orElseThrow());
        assertEquals(MasterNodeStatus.UP, registry.snapshot().get(0).status());
    }

    private MasterNodeRegistryService registry(long timeoutMillis) {
        ClusterProperties properties = new ClusterProperties();
        properties.setHeartbeatTimeoutMillis(timeoutMillis);
        return new MasterNodeRegistryService(properties);
    }
}
