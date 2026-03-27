package com.example.cache.master.cache;

import com.example.cache.master.cache.config.KryoConfig;
import com.example.cache.master.cache.config.SerializationProperties;
import com.example.cache.master.cache.error.SerializationException;
import com.example.cache.master.cache.serialization.BinarySerializationService;
import com.example.cache.master.cache.serialization.dto.BookCacheDto;
import com.example.cache.master.cache.serialization.dto.BookHistoryEventDto;
import com.example.cache.master.cache.serialization.dto.BookMetadataDto;
import com.example.cache.master.cache.serialization.dto.BookRatingDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BinarySerializationServiceTest {

    @Test
    void serializeShouldKeepSmallPayloadUncompressed() {
        BinarySerializationService service = serviceWithThreshold(64);

        byte[] serialized = service.serialize("short-payload");

        assertEquals(0, serialized[0]);
        assertEquals("short-payload", service.deserialize(serialized, String.class));
    }

    @Test
    void serializeShouldCompressLargePayload() {
        BinarySerializationService service = serviceWithThreshold(32);
        String large = "X".repeat(4096);

        byte[] serialized = service.serialize(large);

        assertEquals(1, serialized[0]);
        assertEquals(large, service.deserialize(serialized, String.class));
    }

    @Test
    void deserializeShouldRoundTripBookDto() {
        BinarySerializationService service = serviceWithThreshold(64);
        BookCacheDto expected = new BookCacheDto(
            "book-42",
            "Reactive Java",
            new BookMetadataDto("T. Author", "en", 2025, "isbn-1"),
            List.of(new BookRatingDto("u1", 5, 1730000000000L)),
            List.of(new BookHistoryEventDto("created", 1730000001000L, "imported"))
        );

        byte[] serialized = service.serialize(expected);
        BookCacheDto actual = service.deserialize(serialized, BookCacheDto.class);

        assertEquals(expected, actual);
    }

    @Test
    void deserializeShouldFailForCorruptedPayload() {
        BinarySerializationService service = serviceWithThreshold(64);

        assertThrows(SerializationException.class, () -> service.deserialize(new byte[]{99}, String.class));
    }

    private BinarySerializationService serviceWithThreshold(int thresholdBytes) {
        SerializationProperties properties = new SerializationProperties();
        properties.setCompressionThresholdBytes(thresholdBytes);
        return new BinarySerializationService(new KryoConfig().kryoFactory(), properties);
    }
}
