package com.example.logdownloader.dto;

import java.util.List;

public record UiPreferences(
        String contour,
        String namespace,
        String selector,
        String from,
        String to,
        Boolean previous,
        Long maxBytes,
        Boolean masterAccess,
        List<String> selectedPods,
        List<String> selectedContainers
) {
}
