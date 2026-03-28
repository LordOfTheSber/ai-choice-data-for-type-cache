package com.example.cache.certificate.service;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public record CaMaterial(
        X509Certificate certificate,
        PrivateKey privateKey
) {
}
