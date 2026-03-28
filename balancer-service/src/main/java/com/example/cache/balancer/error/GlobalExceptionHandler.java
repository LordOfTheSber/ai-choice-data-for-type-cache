package com.example.cache.balancer.error;

import com.example.cache.balancer.api.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RouterException.class)
    public ResponseEntity<ErrorResponse> handleRouterError(RouterException exception) {
        HttpStatus status = mapToHttpStatus(exception.getErrorCode());
        log.warn("Router error code={} message={}", exception.getErrorCode(), exception.getMessage());
        return buildResponse(status, exception.getErrorCode().name(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            RouterErrorCode.INTERNAL_ERROR.name(),
            "Unexpected server error"
        );
    }

    private HttpStatus mapToHttpStatus(RouterErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case NO_AVAILABLE_NODE, SLOT_NOT_ASSIGNED -> HttpStatus.SERVICE_UNAVAILABLE;
            case NODE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case REBALANCE_IN_PROGRESS -> HttpStatus.CONFLICT;
            case MASTER_COMMUNICATION_ERROR -> HttpStatus.BAD_GATEWAY;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, String message) {
        ErrorResponse body = new ErrorResponse(code, message, Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}
