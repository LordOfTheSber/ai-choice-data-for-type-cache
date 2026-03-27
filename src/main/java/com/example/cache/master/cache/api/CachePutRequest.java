package com.example.cache.master.cache.api;

import com.example.cache.master.cache.DataClass;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CachePutRequest(
    @NotBlank String payload,
    @NotNull DataClass dataClass,
    @Min(1) long ttlMillis,
    @Min(0) long version
) {
}
