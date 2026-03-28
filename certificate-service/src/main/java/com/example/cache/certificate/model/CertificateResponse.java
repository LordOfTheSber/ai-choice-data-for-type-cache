package com.example.cache.certificate.model;

import java.time.Instant;

public record CertificateResponse(
        String certificatePem,
        String privateKeyPem,
        String caCertificatePem,
        Instant notBefore,
        Instant notAfter
) {
}
