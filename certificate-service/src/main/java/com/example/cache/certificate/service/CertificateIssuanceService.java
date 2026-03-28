package com.example.cache.certificate.service;

import com.example.cache.certificate.config.CertificateProperties;
import com.example.cache.certificate.exception.CertificateErrorCode;
import com.example.cache.certificate.exception.CertificateServiceException;
import com.example.cache.certificate.model.CertificateRequest;
import com.example.cache.certificate.model.CertificateResponse;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateIssuanceService {

    private static final String BC_PROVIDER = "BC";
    private final CertificateProperties certificateProperties;
    private final CertificateAuthorityService certificateAuthorityService;
    private final PemEncodingService pemEncodingService;
    private final SecureRandom secureRandom = new SecureRandom();

    public CertificateResponse issueCertificate(CertificateRequest request) {
        validateTrustedService(request.serviceId());
        try {
            KeyPair keyPair = generateKeyPair();
            CaMaterial caMaterial = certificateAuthorityService.getCaMaterial();
            IssuedCertificate issuedCertificate = issue(request, caMaterial, keyPair);
            return toResponse(issuedCertificate, keyPair, caMaterial);
        } catch (GeneralSecurityException | OperatorCreationException exception) {
            throw new CertificateServiceException(
                    CertificateErrorCode.CERTIFICATE_ISSUANCE_FAILED,
                    "Failed to issue certificate",
                    exception
            );
        }
    }

    private void validateTrustedService(String serviceId) {
        if (certificateProperties.getTrustedServiceIds().contains(serviceId)) {
            return;
        }
        throw new CertificateServiceException(
                CertificateErrorCode.UNTRUSTED_SERVICE,
                "Service ID is not allowed to receive certificate: " + serviceId
        );
    }

    private KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(certificateProperties.getIssuedKeySize(), secureRandom);
        return generator.generateKeyPair();
    }

    private IssuedCertificate issue(CertificateRequest request, CaMaterial caMaterial, KeyPair keyPair)
            throws OperatorCreationException, GeneralSecurityException {
        Instant notBefore = Instant.now().minusSeconds(60);
        Instant notAfter = notBefore.plus(certificateProperties.getIssuedCertificateTtl());
        X509v3CertificateBuilder builder = buildCertificate(request, caMaterial, keyPair, notBefore, notAfter);
        ContentSigner signer = new JcaContentSignerBuilder(certificateProperties.getSignatureAlgorithm())
                .setProvider(BC_PROVIDER)
                .build(caMaterial.privateKey());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BC_PROVIDER).getCertificate(holder);
        certificate.verify(caMaterial.certificate().getPublicKey());
        return new IssuedCertificate(certificate, notBefore, notAfter);
    }

    private X509v3CertificateBuilder buildCertificate(
            CertificateRequest request,
            CaMaterial caMaterial,
            KeyPair keyPair,
            Instant notBefore,
            Instant notAfter
    ) throws OperatorCreationException {
        BigInteger serialNumber = new BigInteger(160, secureRandom).abs();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caMaterial.certificate().getSubjectX500Principal(),
                serialNumber,
                Date.from(notBefore),
                Date.from(notAfter),
                new X500Principal("CN=" + request.commonName()),
                keyPair.getPublic()
        );
        addSubjectAlternativeNames(builder, request.dnsNames());
        return builder;
    }

    private void addSubjectAlternativeNames(X509v3CertificateBuilder builder, List<String> dnsNames)
            throws OperatorCreationException {
        GeneralName[] names = new GeneralName[dnsNames.size()];
        for (int index = 0; index < dnsNames.size(); index++) {
            names[index] = new GeneralName(GeneralName.dNSName, dnsNames.get(index));
        }
        GeneralNames subjectAltNames = GeneralNames.getInstance(new DERSequence(names));
        builder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
    }

    private CertificateResponse toResponse(
            IssuedCertificate issuedCertificate,
            KeyPair keyPair,
            CaMaterial caMaterial
    ) {
        String certificatePem = pemEncodingService.toPem(issuedCertificate.certificate());
        String privateKeyPem = pemEncodingService.toPem(keyPair.getPrivate());
        String caCertificatePem = pemEncodingService.toPem(caMaterial.certificate());
        log.info("Issued certificate for subject={}", issuedCertificate.certificate().getSubjectX500Principal());
        return new CertificateResponse(
                certificatePem,
                privateKeyPem,
                caCertificatePem,
                issuedCertificate.notBefore(),
                issuedCertificate.notAfter()
        );
    }

    private record IssuedCertificate(
            X509Certificate certificate,
            Instant notBefore,
            Instant notAfter
    ) {
    }
}
