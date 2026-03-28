package com.example.cache.balancer.error;

public class RouterException extends RuntimeException {

    private final RouterErrorCode errorCode;

    public RouterException(RouterErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RouterException(RouterErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public RouterErrorCode getErrorCode() {
        return errorCode;
    }
}
