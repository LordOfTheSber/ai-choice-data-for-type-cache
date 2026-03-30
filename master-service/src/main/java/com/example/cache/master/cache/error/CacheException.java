package com.example.cache.master.cache.error;

public class CacheException extends RuntimeException {
    private final CacheErrorCode errorCode;

    public CacheException(CacheErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CacheException(CacheErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public CacheErrorCode getErrorCode() {
        return errorCode;
    }
}
