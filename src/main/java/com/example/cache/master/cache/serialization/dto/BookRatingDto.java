package com.example.cache.master.cache.serialization.dto;

public record BookRatingDto(
    String userId,
    int rating,
    long ratedAtEpochMillis
) {
}
