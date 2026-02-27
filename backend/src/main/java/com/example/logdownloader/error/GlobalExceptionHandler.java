package com.example.logdownloader.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiErrorResponse(Instant.now(), ex.getStatus(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(Instant.now(), 400, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(Instant.now(), 500, ex.getMessage(), request.getRequestURI()));
    }
}
