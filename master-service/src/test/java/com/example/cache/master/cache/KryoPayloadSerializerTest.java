package com.example.cache.master.cache;

import com.example.cache.master.cache.config.KryoConfig;
import com.example.cache.master.cache.config.SerializationProperties;
import com.example.cache.master.cache.error.SerializationException;
import com.example.cache.master.cache.serialization.KryoPayloadSerializer;
import com.example.cache.master.cache.serialization.dto.BookMetadataDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KryoPayloadSerializerTest {

    @Test
    void shouldSerializeAndDeserializeTypedDto() {
        KryoPayloadSerializer serializer = serializer();
        BookMetadataDto expected = new BookMetadataDto("Author", "en", 2024, "isbn");

        byte[] payload = serializer.serialize(expected);
        BookMetadataDto restored = serializer.deserialize(payload, 0, payload.length, BookMetadataDto.class);

        assertEquals(expected, restored);
    }

    @Test
    void shouldFailForTypeMismatch() {
        KryoPayloadSerializer serializer = serializer();
        byte[] payload = serializer.serialize("payload");

        assertThrows(SerializationException.class, () -> serializer.deserialize(payload, 0, payload.length, Integer.class));
    }

    private KryoPayloadSerializer serializer() {
        return new KryoPayloadSerializer(new KryoConfig().kryoFactory(), new SerializationProperties());
    }
}
