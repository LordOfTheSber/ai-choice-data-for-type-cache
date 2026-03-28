package com.example.cache.certificate.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CertificateRequest(
        @NotBlank String serviceId,
        @NotBlank String commonName,
        @NotEmpty List<@NotBlank String> dnsNames
) {
}
