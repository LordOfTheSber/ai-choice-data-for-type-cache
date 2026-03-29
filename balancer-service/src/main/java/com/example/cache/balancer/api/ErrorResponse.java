package com.example.cache.balancer.api;

import java.time.Instant;

public record ErrorResponse(
    String errorCode,
    String message,
    Instant timestamp
) {
}
