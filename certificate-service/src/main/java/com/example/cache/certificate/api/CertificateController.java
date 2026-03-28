package com.example.cache.certificate.api;

import com.example.cache.certificate.model.CertificateRequest;
import com.example.cache.certificate.model.CertificateResponse;
import com.example.cache.certificate.service.CertificateIssuanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateIssuanceService certificateIssuanceService;

    @PostMapping("/issue")
    public ResponseEntity<CertificateResponse> issue(@Valid @RequestBody CertificateRequest request) {
        CertificateResponse response = certificateIssuanceService.issueCertificate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/renew")
    public ResponseEntity<CertificateResponse> renew(@Valid @RequestBody CertificateRequest request) {
        CertificateResponse response = certificateIssuanceService.issueCertificate(request);
        return ResponseEntity.ok(response);
    }
}
