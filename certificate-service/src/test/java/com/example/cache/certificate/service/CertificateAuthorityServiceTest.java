package com.example.cache.certificate.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cache.certificate.config.CertificateProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.time.Duration;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CertificateAuthorityServiceTest {

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void shouldCreateAndReuseCaFiles() throws Exception {
        Path caDirectory = Files.createTempDirectory("ca-service-");
        CertificateProperties properties = buildProperties(caDirectory);

        CertificateAuthorityService firstService = new CertificateAuthorityService(properties);
        firstService.initialize();
        CaMaterial firstMaterial = firstService.getCaMaterial();

        CertificateAuthorityService secondService = new CertificateAuthorityService(properties);
        secondService.initialize();
        CaMaterial secondMaterial = secondService.getCaMaterial();

        assertThat(Files.exists(properties.caCertificatePath())).isTrue();
        assertThat(Files.exists(properties.caPrivateKeyPath())).isTrue();
        assertThat(secondMaterial.certificate().getSerialNumber())
                .isEqualTo(firstMaterial.certificate().getSerialNumber());
    }

    private CertificateProperties buildProperties(Path caDirectory) {
        CertificateProperties properties = new CertificateProperties();
        properties.setCaDirectory(caDirectory);
        properties.setCaCertificateFile("root-ca.crt.pem");
        properties.setCaPrivateKeyFile("root-ca.key.pem");
        properties.setCaPrivateKeyPassword("changeit");
        properties.setIssuedCertificateTtl(Duration.ofHours(2));
        properties.setTrustedServiceIds(Set.of("master"));
        properties.setCaKeySize(2048);
        properties.setIssuedKeySize(2048);
        properties.setSignatureAlgorithm("SHA256withRSA");
        return properties;
    }
}
