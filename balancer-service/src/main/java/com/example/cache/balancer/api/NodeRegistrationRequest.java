package com.example.cache.balancer.api;

public record NodeRegistrationRequest(
    String nodeId,
    String baseUrl,
    int weight
) {
}
