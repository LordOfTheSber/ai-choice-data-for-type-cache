package com.example.logdownloader.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public record PreviewRequest(
        String contour,
        @NotBlank String namespace,
        List<String> pods,
        List<String> containers,
        String selector,
        Instant from,
        Instant to,
        Integer lines,
        boolean previous,
        Long maxBytes,
        boolean bestEffort,
        Integer pollIntervalSeconds,
        boolean masterAccess
) {
}
