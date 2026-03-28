package com.example.cache.certificate.service;

import com.example.cache.certificate.exception.CertificateErrorCode;
import com.example.cache.certificate.exception.CertificateServiceException;
import java.io.IOException;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PemEncodingService {

    public String toPem(X509Certificate certificate) {
        return writePem(certificate);
    }

    public String toPem(PrivateKey privateKey) {
        return writePem(privateKey);
    }

    private String writePem(Object value) {
        try (StringWriter writer = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(value);
            pemWriter.flush();
            return writer.toString();
        } catch (IOException exception) {
            log.error("Failed to encode value into PEM", exception);
            throw new CertificateServiceException(
                    CertificateErrorCode.CERTIFICATE_ISSUANCE_FAILED,
                    "Failed to encode certificate material",
                    exception
            );
        }
    }
}
