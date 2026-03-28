package com.example.cache.master.cache.config;

import com.esotericsoftware.kryo.Kryo;
import com.example.cache.master.cache.serialization.dto.BookCacheDto;
import com.example.cache.master.cache.serialization.dto.BookHistoryEventDto;
import com.example.cache.master.cache.serialization.dto.BookMetadataDto;
import com.example.cache.master.cache.serialization.dto.BookRatingDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;

@Configuration
public class KryoConfig {

    @Bean
    public KryoFactory kryoFactory() {
        return this::createConfiguredKryo;
    }

    private Kryo createConfiguredKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        registerCoreDtos(kryo);
        return kryo;
    }

    private void registerCoreDtos(Kryo kryo) {
        kryo.register(BookCacheDto.class, 100);
        kryo.register(BookMetadataDto.class, 101);
        kryo.register(BookRatingDto.class, 102);
        kryo.register(BookHistoryEventDto.class, 103);
        kryo.register(ArrayList.class, 104);
        kryo.register(HashMap.class, 105);
        // Для новых cache DTO: добавляйте стабильный ID в этой секции,
        // чтобы сохранить совместимость сериализации между релизами.
    }

    @FunctionalInterface
    public interface KryoFactory {
        Kryo create();
    }
}
