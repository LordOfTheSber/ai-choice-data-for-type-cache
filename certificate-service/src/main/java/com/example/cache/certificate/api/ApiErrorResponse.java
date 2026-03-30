package com.example.cache.certificate.api;

import com.example.cache.certificate.exception.CertificateErrorCode;
import java.time.Instant;

public record ApiErrorResponse(
        CertificateErrorCode errorCode,
        String message,
        Instant timestamp
) {
}
