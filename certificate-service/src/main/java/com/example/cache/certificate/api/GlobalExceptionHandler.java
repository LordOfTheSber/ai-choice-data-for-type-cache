package com.example.cache.certificate.api;

import com.example.cache.certificate.exception.CertificateErrorCode;
import com.example.cache.certificate.exception.CertificateServiceException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldMessage)
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, CertificateErrorCode.INVALID_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return error(HttpStatus.BAD_REQUEST, CertificateErrorCode.INVALID_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(CertificateServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleServiceException(CertificateServiceException exception) {
        HttpStatus status = resolveHttpStatus(exception.getErrorCode());
        return error(status, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                CertificateErrorCode.CERTIFICATE_ISSUANCE_FAILED,
                "Unexpected server error"
        );
    }

    private String toFieldMessage(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private HttpStatus resolveHttpStatus(CertificateErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNTRUSTED_SERVICE -> HttpStatus.FORBIDDEN;
            case CA_INITIALIZATION_FAILED, CERTIFICATE_ISSUANCE_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, CertificateErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, Instant.now()));
    }
}
