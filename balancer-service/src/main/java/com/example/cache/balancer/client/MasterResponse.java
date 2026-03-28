package com.example.cache.balancer.client;

import org.springframework.http.HttpStatusCode;

/**
 * Wrapper around the HTTP response received from a master node.
 */
public record MasterResponse(
    HttpStatusCode status,
    String body
) {

    public boolean isSuccess() {
        return status.is2xxSuccessful();
    }

    public boolean isNotFound() {
        return status.value() == 404;
    }
}
