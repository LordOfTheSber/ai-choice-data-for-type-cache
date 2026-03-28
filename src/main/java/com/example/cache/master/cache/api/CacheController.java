package com.example.cache.master.cache.api;

import com.example.cache.master.cache.CacheStatus;
import com.example.cache.master.cache.CacheValue;
import com.example.cache.master.cache.DataClass;
import com.example.cache.master.cache.MasterCacheService;
import com.example.cache.master.cache.error.CacheErrorCode;
import com.example.cache.master.cache.error.CacheException;
import com.example.cache.master.cache.serialization.BinarySerializationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/cache")
public class CacheController {
    private static final String HEADER_PAYLOAD_TYPE = "X-Payload-Type";
    private static final String HEADER_DATA_CLASS = "X-Data-Class";
    private static final String HEADER_TTL_MILLIS = "X-Ttl-Millis";
    private static final String HEADER_VERSION = "X-Version";

    private final MasterCacheService masterCacheService;
    private final BinarySerializationService binarySerializationService;
    private final ConcurrentHashMap<String, Class<?>> typeCache;

    public CacheController(MasterCacheService masterCacheService,
                           BinarySerializationService binarySerializationService) {
        this.masterCacheService = masterCacheService;
        this.binarySerializationService = binarySerializationService;
        this.typeCache = new ConcurrentHashMap<>();
    }

    @GetMapping("/{key}")
    public ResponseEntity<CacheGetResponse> get(@PathVariable String key) {
        return masterCacheService.get(key)
            .map(value -> ResponseEntity.ok(toResponse(key, value)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{key}/binary", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getBinary(@PathVariable String key) {
        Optional<CacheValue> value = masterCacheService.get(key);
        if (value.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CacheValue cacheValue = value.get();
        byte[] payload = binarySerializationService.serialize(cacheValue.getValue());
        return ResponseEntity.ok().headers(binaryHeaders(cacheValue)).body(payload);
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

    @PutMapping(value = "/{key}/binary", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> putBinary(@PathVariable String key,
                                          @RequestHeader(HEADER_PAYLOAD_TYPE) String typeName,
                                          @RequestHeader(HEADER_DATA_CLASS) DataClass dataClass,
                                          @RequestHeader(HEADER_TTL_MILLIS) long ttlMillis,
                                          @RequestHeader(HEADER_VERSION) long version,
                                          @RequestBody byte[] payload) {
        validateBinaryRequest(typeName, dataClass, ttlMillis, version, payload);
        Class<?> payloadType = resolveType(typeName);
        Object value = binarySerializationService.deserialize(payload, payloadType);
        CacheValue cacheValue = new CacheValue(value, dataClass, Duration.ofMillis(ttlMillis), CacheStatus.COLD, version);
        masterCacheService.put(key, cacheValue);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        masterCacheService.delete(key);
        return ResponseEntity.noContent().build();
    }

    private HttpHeaders binaryHeaders(CacheValue value) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add(HEADER_PAYLOAD_TYPE, typeName(value.getValue()));
        headers.add(HEADER_DATA_CLASS, value.getDataClass().name());
        headers.add(HEADER_TTL_MILLIS, String.valueOf(value.getTtl().toMillis()));
        headers.add(HEADER_VERSION, String.valueOf(value.getVersion()));
        return headers;
    }

    private void validatePutRequest(CachePutRequest request) {
        if (request == null) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "request must not be null");
        }
        if (request.payload() == null || request.payload().isBlank()) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "payload must not be blank");
        }
        if (request.dataClass() == null) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "dataClass must not be null");
        }
        if (request.ttlMillis() <= 0L || request.version() < 0L) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "ttlMillis/version are invalid");
        }
    }

    private void validateBinaryRequest(String typeName,
                                       DataClass dataClass,
                                       long ttlMillis,
                                       long version,
                                       byte[] payload) {
        if (typeName == null || typeName.isBlank()) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "payload type header must not be blank");
        }
        if (dataClass == null || ttlMillis <= 0L || version < 0L) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "binary metadata headers are invalid");
        }
        if (payload == null || payload.length == 0) {
            throw new CacheException(CacheErrorCode.VALIDATION_ERROR, "binary payload must not be empty");
        }
    }

    private Class<?> resolveType(String typeName) {
        return typeCache.computeIfAbsent(typeName, this::loadType);
    }

    private Class<?> loadType(String typeName) {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException exception) {
            throw new CacheException(CacheErrorCode.SERIALIZATION_ERROR, "Unknown payload type=" + typeName, exception);
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

    private String payloadAsString(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(payload);
    }

    private String typeName(Object value) {
        return value == null ? Object.class.getName() : value.getClass().getName();
    }
}
