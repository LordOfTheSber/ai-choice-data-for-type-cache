package com.example.cache.master.cache.api;

import com.example.cache.master.cache.CacheStatus;
import com.example.cache.master.cache.CacheValue;
import com.example.cache.master.cache.MasterCacheService;
import com.example.cache.master.cache.error.CacheErrorCode;
import com.example.cache.master.cache.error.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequestMapping("/api/cache")
public class CacheController {
    private final MasterCacheService masterCacheService;
    private final ObjectMapper objectMapper;

    public CacheController(MasterCacheService masterCacheService, ObjectMapper objectMapper) {
        this.masterCacheService = masterCacheService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{key}")
    public ResponseEntity<CacheGetResponse> get(@PathVariable String key) {
        return masterCacheService.get(key)
            .map(value -> ResponseEntity.ok(toResponse(key, value)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{key}/typed")
    public ResponseEntity<CacheTypedGetResponse> getTyped(@PathVariable String key) {
        return masterCacheService.get(key)
            .map(value -> ResponseEntity.ok(toTypedResponse(key, value)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{key}")
    public ResponseEntity<Void> put(@PathVariable String key, @RequestBody CachePutRequest request) {
        validatePutRequest(request);
        CacheValue value = new CacheValue(
            request.payload(),
            request.dataClass(),
            Duration.ofMillis(request.ttlMillis()),
            CacheStatus.COLD,
            request.version()
        );
        masterCacheService.put(key, value);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PutMapping("/{key}/typed")
    public ResponseEntity<Void> putTyped(@PathVariable String key, @RequestBody CacheTypedPutRequest request) {
        validateTypedPutRequest(request);
        Object payload = toTypedPayload(request.typeName(), request.payload());
        CacheValue value = new CacheValue(
            payload,
            request.dataClass(),
            Duration.ofMillis(request.ttlMillis()),
            CacheStatus.COLD,
            request.version()
        );
        masterCacheService.put(key, value);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        masterCacheService.delete(key);
        return ResponseEntity.noContent().build();
    }

    private void validatePutRequest(CachePutRequest request) {
        if (request == null) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "request must not be null");
        }
        if (request.payload() == null || request.payload().isBlank()) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "payload must not be blank");
        }
        validateSharedFields(request.dataClass(), request.ttlMillis(), request.version());
    }

    private void validateTypedPutRequest(CacheTypedPutRequest request) {
        if (request == null) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "request must not be null");
        }
        if (request.typeName() == null || request.typeName().isBlank()) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "typeName must not be blank");
        }
        if (request.payload() == null || request.payload().isNull()) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "payload must not be null");
        }
        validateSharedFields(request.dataClass(), request.ttlMillis(), request.version());
    }

    private void validateSharedFields(Object dataClass, long ttlMillis, long version) {
        if (dataClass == null) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "dataClass must not be null");
        }
        if (ttlMillis <= 0L || version < 0L) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "ttlMillis/version are invalid");
        }
    }

    private Object toTypedPayload(String typeName, JsonNode payload) {
        try {
            Class<?> type = Class.forName(typeName);
            return objectMapper.treeToValue(payload, type);
        } catch (ClassNotFoundException | JsonProcessingException exception) {
            throw new CacheException(
                CacheErrorCode.SERIALIZATION_ERROR,
                "Failed to materialize payload for type=" + typeName,
                exception
            );
        }
    }

    private CacheGetResponse toResponse(String key, CacheValue value) {
        String payload = payloadAsString(value.getValue());
        long ttlMillis = value.getTtl().toMillis();
        return new CacheGetResponse(
            key,
            payload,
            value.getDataClass(),
            ttlMillis,
            value.getVersion(),
            value.getStatus()
        );
    }

    private CacheTypedGetResponse toTypedResponse(String key, CacheValue value) {
        return new CacheTypedGetResponse(
            key,
            typeName(value.getValue()),
            value.getValue(),
            value.getDataClass(),
            value.getTtl().toMillis(),
            value.getVersion(),
            value.getStatus()
        );
    }

    private String typeName(Object payload) {
        return payload == null ? Object.class.getName() : payload.getClass().getName();
    }

    private String payloadAsString(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(payload);
    }
}
