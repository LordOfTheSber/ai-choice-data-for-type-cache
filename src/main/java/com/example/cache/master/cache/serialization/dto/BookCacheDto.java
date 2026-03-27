package com.example.cache.master.cache.serialization.dto;

import java.util.List;

public record BookCacheDto(
    String bookId,
    String title,
    BookMetadataDto metadata,
    List<BookRatingDto> ratings,
    List<BookHistoryEventDto> history
) {
}
