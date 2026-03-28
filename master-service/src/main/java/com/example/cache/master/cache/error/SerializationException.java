package com.example.cache.master.cache.error;

public class SerializationException extends CacheException {

    public SerializationException(String message, Throwable cause) {
        super(CacheErrorCode.SERIALIZATION_ERROR, message, cause);
    }

    public SerializationException(String message) {
        super(CacheErrorCode.SERIALIZATION_ERROR, message);
    }
}
