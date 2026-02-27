package com.example.logdownloader.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public record DownloadRequest(
        String contour,
        @NotBlank String namespace,
        List<String> pods,
        List<String> containers,
        String selector,
        String workloadKind,
        String workloadName,
        Instant from,
        Instant to,
        boolean previous,
        Long maxBytes,
        boolean bestEffort,
        Integer pollIntervalSeconds,
        boolean masterAccess
) {
}
