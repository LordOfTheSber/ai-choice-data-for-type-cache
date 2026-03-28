package com.example.cache.certificate.exception;

import lombok.Getter;

@Getter
public class CertificateServiceException extends RuntimeException {

    private final CertificateErrorCode errorCode;

    public CertificateServiceException(CertificateErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CertificateServiceException(CertificateErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
