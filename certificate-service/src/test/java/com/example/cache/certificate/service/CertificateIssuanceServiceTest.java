package com.example.cache.certificate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.cache.certificate.config.CertificateProperties;
import com.example.cache.certificate.exception.CertificateErrorCode;
import com.example.cache.certificate.exception.CertificateServiceException;
import com.example.cache.certificate.model.CertificateRequest;
import com.example.cache.certificate.model.CertificateResponse;
import com.example.cache.certificate.support.TestCertificateFactory;
import java.security.KeyPair;
import java.security.Security;
import java.time.Duration;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CertificateIssuanceServiceTest {

    @Mock
    private CertificateAuthorityService certificateAuthorityService;

    private CertificateProperties certificateProperties;
    private CertificateIssuanceService certificateIssuanceService;

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @BeforeEach
    void setUp() {
        certificateProperties = new CertificateProperties();
        certificateProperties.setIssuedCertificateTtl(Duration.ofHours(1));
        certificateProperties.setIssuedKeySize(2048);
        certificateProperties.setSignatureAlgorithm("SHA256withRSA");
        certificateProperties.setTrustedServiceIds(Set.of("master", "db-service"));
        certificateIssuanceService = new CertificateIssuanceService(
                certificateProperties,
                certificateAuthorityService,
                new PemEncodingService()
        );
    }

    @Test
    void shouldIssueCertificateForTrustedService() throws Exception {
        KeyPair caKeyPair = TestCertificateFactory.generateRsaKeyPair(2048);
        CaMaterial material = new CaMaterial(
                TestCertificateFactory.selfSigned(caKeyPair, "Root CA"),
                caKeyPair.getPrivate()
        );
        when(certificateAuthorityService.getCaMaterial()).thenReturn(material);
        CertificateRequest request = new CertificateRequest(
                "master",
                "master.internal",
                java.util.List.of("master.internal")
        );

        CertificateResponse response = certificateIssuanceService.issueCertificate(request);

        assertThat(response.certificatePem()).contains("BEGIN CERTIFICATE");
        assertThat(response.privateKeyPem()).contains("BEGIN PRIVATE KEY");
        assertThat(response.caCertificatePem()).contains("BEGIN CERTIFICATE");
        assertThat(response.notAfter()).isAfter(response.notBefore());
    }

    @Test
    void shouldRejectUntrustedServiceId() {
        CertificateRequest request = new CertificateRequest(
                "unknown",
                "bad.internal",
                java.util.List.of("bad.internal")
        );

        assertThatThrownBy(() -> certificateIssuanceService.issueCertificate(request))
                .isInstanceOf(CertificateServiceException.class)
                .extracting(exception -> ((CertificateServiceException) exception).getErrorCode())
                .isEqualTo(CertificateErrorCode.UNTRUSTED_SERVICE);
    }
}
