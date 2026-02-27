package com.example.logdownloader.dto;

public record LogCollectStatusResponse(String jobId, String status, String message, long sizeBytes) {
}
