package com.example.cache.master.cache.serialization.dto;

public record BookHistoryEventDto(
    String eventType,
    long occurredAtEpochMillis,
    String details
) {
}
