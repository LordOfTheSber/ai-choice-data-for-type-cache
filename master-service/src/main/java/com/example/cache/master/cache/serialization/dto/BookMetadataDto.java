package com.example.cache.master.cache.serialization.dto;

public record BookMetadataDto(
    String author,
    String language,
    int publicationYear,
    String isbn
) {
}
